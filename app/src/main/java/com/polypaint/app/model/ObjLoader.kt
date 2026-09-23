package com.polypaint.app.model

import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Minimal but robust Wavefront OBJ + MTL loader.
 *
 * Supports v / vt / vn / f (triangles, quads, and convex n-gons via fan
 * triangulation), usemtl / mtllib, and negative (relative) face indices.
 * Vertices are deduplicated by (position, uv, normal) triplet so the result
 * can be drawn with a single indexed draw call.
 *
 * [objFile] must be a real file on local storage, not a content:// Uri -
 * see ImportUtils, which stages a picked document into app-private cache
 * storage first. That's what lets this loader resolve a companion .mtl
 * (and the .mtl's texture references) as plain sibling files.
 *
 * Known limitations (see README for the full list): this does not unwrap
 * UVs for you - the source .obj must already carry texture coordinates for
 * UV painting to be meaningful - and non-convex n-gon faces triangulate
 * imperfectly, which is fine for the common quad/tri case most exporters
 * produce.
 */
object ObjLoader {

    class Result(val mesh: ObjMesh, val warnings: List<String>)

    fun load(objFile: File): Result {
        val warnings = mutableListOf<String>()
        val positions = mutableListOf<Vec3>()
        val uvs = mutableListOf<Vec2>()
        val normals = mutableListOf<Vec3>()

        val vertexCache = HashMap<Triple<Int, Int, Int>, Int>()
        val finalPositions = mutableListOf<Vec3>()
        val finalUvs = mutableListOf<Vec2>()
        val finalNormals = mutableListOf<Vec3>()
        val tangentAccum = mutableListOf<Vec3>()
        val signAccum = mutableListOf<Float>()

        var currentMaterial: String? = null
        val subMeshIndices = LinkedHashMap<String, MutableList<Int>>()
        var mtlFileName: String? = null
        var hadExplicitNormals = false

        fun getOrCreateVertex(pi: Int, ti: Int, ni: Int): Int {
            val key = Triple(pi, ti, ni)
            vertexCache[key]?.let { return it }
            val pos = positions.getOrElse(pi) { Vec3(0f, 0f, 0f) }
            val uv = if (ti >= 0) uvs.getOrElse(ti) { Vec2(0f, 0f) } else Vec2(0f, 0f)
            val nrm = if (ni >= 0) normals.getOrElse(ni) { Vec3(0f, 0f, 1f) } else Vec3(0f, 0f, 1f)
            val idx = finalPositions.size
            finalPositions.add(pos); finalUvs.add(uv); finalNormals.add(nrm)
            tangentAccum.add(Vec3(0f, 0f, 0f)); signAccum.add(0f)
            vertexCache[key] = idx
            return idx
        }

        fun resolveIndex(raw: Int, count: Int) = if (raw < 0) count + raw else raw - 1

        if (!objFile.exists()) {
            return Result(
                ObjMesh(emptyList(), emptyList(), emptyMap(), Vec3(0f, 0f, 0f), Vec3(0f, 0f, 0f)),
                listOf("File not found: ${objFile.name}")
            )
        }

        objFile.bufferedReader().useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val tokens = line.split(Regex("\\s+"))
                when (tokens[0]) {
                    "v" -> if (tokens.size >= 4) positions.add(
                        Vec3(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())
                    )
                    "vt" -> if (tokens.size >= 3) uvs.add(Vec2(tokens[1].toFloat(), tokens[2].toFloat()))
                    "vn" -> if (tokens.size >= 4) {
                        hadExplicitNormals = true
                        normals.add(Vec3(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat()).normalized())
                    }
                    "mtllib" -> mtlFileName = tokens.getOrNull(1)
                    "usemtl" -> currentMaterial = tokens.getOrNull(1)
                    "f" -> {
                        val matKey = currentMaterial ?: ""
                        val faceVerts = tokens.drop(1).map { tok ->
                            val parts = tok.split("/")
                            val pi = resolveIndex(parts[0].toInt(), positions.size)
                            val ti = if (parts.size > 1 && parts[1].isNotEmpty()) resolveIndex(parts[1].toInt(), uvs.size) else -1
                            val ni = if (parts.size > 2 && parts[2].isNotEmpty()) resolveIndex(parts[2].toInt(), normals.size) else -1
                            getOrCreateVertex(pi, ti, ni)
                        }
                        if (faceVerts.size >= 3) {
                            val list = subMeshIndices.getOrPut(matKey) { mutableListOf() }
                            for (i in 1 until faceVerts.size - 1) {
                                list.add(faceVerts[0]); list.add(faceVerts[i]); list.add(faceVerts[i + 1])
                                accumulateTangent(
                                    faceVerts[0], faceVerts[i], faceVerts[i + 1],
                                    finalPositions, finalUvs, tangentAccum, signAccum
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }

        if (uvs.isEmpty()) {
            warnings.add("Model has no UV coordinates - UV painting won't be meaningful until it's UV-unwrapped in a 3D tool.")
        }
        if (!hadExplicitNormals) {
            warnings.add("Model had no normals - generated flat per-face normals.")
            recomputeFlatNormals(finalPositions, subMeshIndices, finalNormals)
        }

        val vertices = ArrayList<Vertex>(finalPositions.size)
        for (i in finalPositions.indices) {
            val n = finalNormals[i]
            val rawT = tangentAccum[i]
            val t = if (rawT.length() < 1e-6f) {
                (if (abs(n.x) < 0.9f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)).cross(n).normalized()
            } else {
                val ortho = rawT - n * n.dot(rawT)
                if (ortho.length() > 1e-6f) ortho.normalized() else Vec3(1f, 0f, 0f)
            }
            val handedness = if (signAccum[i] < 0f) -1f else 1f
            vertices.add(Vertex(finalPositions[i], n, finalUvs[i], t, handedness))
        }

        var minV = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var maxV = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (p in finalPositions) {
            minV = Vec3(min(minV.x, p.x), min(minV.y, p.y), min(minV.z, p.z))
            maxV = Vec3(max(maxV.x, p.x), max(maxV.y, p.y), max(maxV.z, p.z))
        }
        if (finalPositions.isEmpty()) {
            minV = Vec3(0f, 0f, 0f); maxV = Vec3(0f, 0f, 0f)
        }

        val materials = mtlFileName?.let { name ->
            val mtlFile = File(objFile.parentFile, name)
            if (mtlFile.exists()) {
                try {
                    MtlIO.read(mtlFile)
                } catch (e: Exception) {
                    warnings.add("Could not parse ${mtlFile.name}: ${e.message}")
                    emptyMap()
                }
            } else {
                warnings.add("Referenced material file '$name' was not found next to the .obj.")
                emptyMap()
            }
        } ?: emptyMap()

        val subMeshes = subMeshIndices.map { (mat, idx) -> SubMesh(mat.ifEmpty { null }, idx.toIntArray()) }
        return Result(ObjMesh(vertices, subMeshes, materials, minV, maxV), warnings)
    }

    /** Standard tangent-space basis derivation (Lengyel's method): for each
     *  triangle, solve for the tangent/bitangent that make the UV gradient
     *  consistent, then accumulate per-vertex for averaging across faces. */
    private fun accumulateTangent(
        i0: Int, i1: Int, i2: Int,
        positions: List<Vec3>, uvsList: List<Vec2>,
        tangentAccum: MutableList<Vec3>, signAccum: MutableList<Float>
    ) {
        val p0 = positions[i0]; val p1 = positions[i1]; val p2 = positions[i2]
        val uv0 = uvsList[i0]; val uv1 = uvsList[i1]; val uv2 = uvsList[i2]
        val e1 = p1 - p0; val e2 = p2 - p0
        val du1 = uv1.x - uv0.x; val dv1 = uv1.y - uv0.y
        val du2 = uv2.x - uv0.x; val dv2 = uv2.y - uv0.y
        val denom = du1 * dv2 - du2 * dv1
        val f = if (abs(denom) > 1e-8f) 1f / denom else 0f
        val tangent = Vec3(f * (dv2 * e1.x - dv1 * e2.x), f * (dv2 * e1.y - dv1 * e2.y), f * (dv2 * e1.z - dv1 * e2.z))
        val bitangent = Vec3(f * (-du2 * e1.x + du1 * e2.x), f * (-du2 * e1.y + du1 * e2.y), f * (-du2 * e1.z + du1 * e2.z))
        val faceNormal = e1.cross(e2)
        val sign = if (faceNormal.cross(tangent).dot(bitangent) < 0f) -1f else 1f
        for (i in intArrayOf(i0, i1, i2)) {
            tangentAccum[i] = tangentAccum[i] + tangent
            signAccum[i] = signAccum[i] + sign
        }
    }

    private fun recomputeFlatNormals(
        positions: List<Vec3>,
        subMeshIndices: Map<String, MutableList<Int>>,
        normalsOut: MutableList<Vec3>
    ) {
        val accum = Array(positions.size) { Vec3(0f, 0f, 0f) }
        for ((_, idx) in subMeshIndices) {
            var i = 0
            while (i + 2 < idx.size) {
                val a = idx[i]; val b = idx[i + 1]; val c = idx[i + 2]
                val n = (positions[b] - positions[a]).cross(positions[c] - positions[a])
                accum[a] = accum[a] + n; accum[b] = accum[b] + n; accum[c] = accum[c] + n
                i += 3
            }
        }
        for (i in normalsOut.indices) normalsOut[i] = accum[i].normalized()
    }
}
