package com.polypaint.app.render

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Simple orbit camera: yaw/pitch around a target point, with distance (zoom). */
class OrbitCamera {
    var yaw: Float = 0.6f
    var pitch: Float = 0.35f
    var distance: Float = 3f
    var target: FloatArray = floatArrayOf(0f, 0f, 0f)

    private val minPitch = -1.5f
    private val maxPitch = 1.5f

    fun rotate(dYaw: Float, dPitch: Float) {
        yaw -= dYaw
        pitch = min(maxPitch, max(minPitch, pitch - dPitch))
    }

    fun zoom(factor: Float) {
        distance = min(50f, max(0.05f, distance * factor))
    }

    fun eye(): FloatArray {
        val x = target[0] + distance * cos(pitch) * sin(yaw)
        val y = target[1] + distance * sin(pitch)
        val z = target[2] + distance * cos(pitch) * cos(yaw)
        return floatArrayOf(x, y, z)
    }

    fun viewMatrix(): FloatArray {
        val m = FloatArray(16)
        val e = eye()
        Matrix.setLookAtM(m, 0, e[0], e[1], e[2], target[0], target[1], target[2], 0f, 1f, 0f)
        return m
    }

    /** Frames the camera so [radius] fills a comfortable portion of the view. */
    fun frame(center: FloatArray, radius: Float) {
        target = center.copyOf()
        distance = max(0.2f, radius * 2.4f)
    }
}

fun projectionMatrix(fovYDegrees: Float, aspect: Float, near: Float, far: Float): FloatArray {
    val m = FloatArray(16)
    Matrix.perspectiveM(m, 0, fovYDegrees, aspect, near, far)
    return m
}
