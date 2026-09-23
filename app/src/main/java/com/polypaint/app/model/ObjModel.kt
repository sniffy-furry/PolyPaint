package com.polypaint.app.model

data class Vec2(val x: Float, val y: Float)

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = kotlin.math.sqrt(dot(this))
    fun normalized(): Vec3 {
        val l = length()
        return if (l > 1e-8f) Vec3(x / l, y / l, z / l) else Vec3(0f, 0f, 1f)
    }
}

/** One GPU-ready vertex: position, shading normal, UV, and tangent (xyz)
 *  plus handedness (w) for tangent-space normal mapping. */
data class Vertex(
    val position: Vec3,
    val normal: Vec3,
    val uv: Vec2,
    val tangent: Vec3 = Vec3(1f, 0f, 0f),
    val handedness: Float = 1f
)

data class SubMesh(
    val materialName: String?,
    val indices: IntArray
)

/**
 * A fully-loaded, GPU-ready mesh: one deduplicated vertex buffer, plus the
 * index lists the source .obj's `usemtl` groups were split into (PolyPaint
 * still paints a single unified texture set across the whole mesh - see
 * the README - but the original grouping is kept around for export).
 */
class ObjMesh(
    val vertices: List<Vertex>,
    val subMeshes: List<SubMesh>,
    val materials: Map<String, MtlMaterial>,
    val boundsMin: Vec3,
    val boundsMax: Vec3
) {
    val center: Vec3 get() = (boundsMin + boundsMax) * 0.5f
    val radius: Float get() = (boundsMax - boundsMin).length() * 0.5f
}

data class MtlMaterial(
    val name: String,
    var albedoMapPath: String? = null,
    var normalMapPath: String? = null,
    var roughnessMapPath: String? = null,
    var metallicMapPath: String? = null,
    var aoMapPath: String? = null,
    var emissiveMapPath: String? = null,
    var baseColor: Vec3 = Vec3(0.8f, 0.8f, 0.8f),
    var roughness: Float = 0.6f,
    var metallic: Float = 0f
)
