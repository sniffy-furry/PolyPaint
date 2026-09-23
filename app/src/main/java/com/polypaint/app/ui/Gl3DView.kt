package com.polypaint.app.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.polypaint.app.model.ObjMesh
import com.polypaint.app.render.GLModelRenderer
import com.polypaint.app.render.Raycast
import com.polypaint.app.render.projectionMatrix

enum class ViewMode { VIEW, PAINT }

/**
 * GLSurfaceView subclass that owns a GLModelRenderer and turns touch
 * gestures into either camera orbit (ViewMode.VIEW) or paint raycasts
 * (ViewMode.PAINT), depending on [mode].
 */
class Gl3DView(context: Context) : GLSurfaceView(context) {

    val modelRenderer = GLModelRenderer()
    var mode: ViewMode = ViewMode.VIEW
    var currentMesh: ObjMesh? = null
    var onPaintHit: ((u: Float, v: Float) -> Unit)? = null
    var onStrokeEnd: (() -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f
    private var lastPaintX = 0f
    private var lastPaintY = 0f
    private val minPaintSamplePx = 6f

    init {
        setEGLContextClientVersion(3)
        setRenderer(modelRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                if (mode == ViewMode.PAINT) {
                    lastPaintX = event.x; lastPaintY = event.y
                    tryPaint(event.x, event.y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == ViewMode.VIEW || pointerCount > 1) {
                    if (pointerCount <= 1) {
                        val dx = (event.x - lastX) * 0.01f
                        val dy = (event.y - lastY) * 0.01f
                        modelRenderer.camera.rotate(dx, dy)
                    }
                    lastX = event.x; lastY = event.y
                } else {
                    val dist = kotlin.math.hypot((event.x - lastPaintX).toDouble(), (event.y - lastPaintY).toDouble())
                    if (dist >= minPaintSamplePx) {
                        tryPaint(event.x, event.y)
                        lastPaintX = event.x; lastPaintY = event.y
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode == ViewMode.PAINT) onStrokeEnd?.invoke()
            }
        }
        return true
    }

    private fun tryPaint(x: Float, y: Float) {
        val mesh = currentMesh ?: return
        if (width <= 0 || height <= 0) return
        val cam = modelRenderer.camera
        val view = cam.viewMatrix()
        val proj = projectionMatrix(45f, width.toFloat() / height.toFloat(), 0.01f, 100f)
        val hit = Raycast.pick(mesh, x, y, width, height, view, proj) ?: return
        onPaintHit?.invoke(hit.uv.x, hit.uv.y)
    }
}
