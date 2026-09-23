package com.polypaint.app.render

import android.opengl.Matrix
import com.polypaint.app.model.ObjMesh
import com.polypaint.app.model.Vec2
import com.polypaint.app.model.Vec3

data class RayHit(val uv: Vec2, val worldPos: Vec3, val distance: Float)

object Raycast {

    /**
     * Converts a touch point (view pixels) into a world-space ray, finds
     * the closest triangle it hits, and returns the barycentric-interpolated
     * UV there - that UV is what actually gets painted into the texture.
     *
     * This is a brute-force test over every triangle. That's fine for
     * typical mobile-asset polycounts (tens of thousands of triangles);
     * a spatial index (BVH / uniform grid) would be the natural next step
     * for denser meshes - see README.
     */
    fun pick(
        mesh: ObjMesh,
        touchXPx: Float, touchYPx: Float,
        viewWidthPx: Int, viewHeightPx: Int,
        view: FloatArray, projection: FloatArray
    ): RayHit? {
        if (viewWidthPx <= 0 || viewHeightPx <= 0) return null
        val ndcX = (2f * touchXPx / viewWidthPx) - 1f
        val ndcY = 1f - (2f * touchYPx / viewHeightPx)

        val viewProj = FloatArray(16)
        Matrix.multiplyMM(viewProj, 0, projection, 0, view, 0)
        val invVP = FloatArray(16)
        if (!Matrix.invertM(invVP, 0, viewProj, 0)) return null

        val nearP = unproject(invVP, ndcX, ndcY, -1f)
        val farP = unproject(invVP, ndcX, ndcY, 1f)
        val origin = nearP
        val dir = (farP - nearP).normalized()

        var closest: RayHit? = null
        var closestT = Float.MAX_VALUE

        for (sub in mesh.subMeshes) {
            val idx = sub.indices
            var i = 0
            while (i + 2 < idx.size) {
                val a = mesh.vertices[idx[i]]
                val b = mesh.vertices[idx[i + 1]]
                val c = mesh.vertices[idx[i + 2]]
                val hit = intersectTriangle(origin, dir, a.position, b.position, c.position)
                if (hit != null && hit.first < closestT) {
                    closestT = hit.first
                    val (wa, wb, wc) = hit.second
                    val uv = Vec2(
                        wa * a.uv.x + wb * b.uv.x + wc * c.uv.x,
                        wa * a.uv.y + wb * b.uv.y + wc * c.uv.y
                    )
                    closest = RayHit(uv, origin + dir * hit.first, hit.first)
                }
                i += 3
            }
        }
        return closest
    }

    private fun unproject(invVP: FloatArray, ndcX: Float, ndcY: Float, ndcZ: Float): Vec3 {
        val clip = floatArrayOf(ndcX, ndcY, ndcZ, 1f)
        val world = FloatArray(4)
        Matrix.multiplyMV(world, 0, invVP, 0, clip, 0)
        val w = if (kotlin.math.abs(world[3]) > 1e-8f) world[3] else 1e-8f
        return Vec3(world[0] / w, world[1] / w, world[2] / w)
    }

    /** Moller-Trumbore. Returns (t, (weightA, weightB, weightC)). */
    private fun intersectTriangle(
        origin: Vec3, dir: Vec3, a: Vec3, b: Vec3, c: Vec3
    ): Pair<Float, Triple<Float, Float, Float>>? {
        val eps = 1e-6f
        val edge1 = b - a
        val edge2 = c - a
        val h = dir.cross(edge2)
        val det = edge1.dot(h)
        if (kotlin.math.abs(det) < eps) return null
        val invDet = 1f / det
        val s = origin - a
        val u = s.dot(h) * invDet
        if (u < 0f || u > 1f) return null
        val q = s.cross(edge1)
        val v = dir.dot(q) * invDet
        if (v < 0f || u + v > 1f) return null
        val t = edge2.dot(q) * invDet
        if (t <= eps) return null
        return t to Triple(1f - u - v, u, v)
    }
}
