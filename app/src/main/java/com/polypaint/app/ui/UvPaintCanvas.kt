package com.polypaint.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.polypaint.app.model.ObjMesh

/**
 * The "plain sheet" view: shows the active channel's texture with the UV
 * wireframe on top, and turns drags into paint stamps in texture-pixel
 * space (PAINT mode) or pan/zoom (VIEW mode).
 *
 * [paintVersion] exists purely to invalidate the cached ImageBitmap: the
 * underlying Bitmap is painted into in place (see TextureLayerManager), and
 * Compose has no way to know those pixels changed unless something it's
 * tracking - this counter - changes too.
 */
@Composable
fun UvPaintCanvas(
    bitmap: Bitmap,
    paintVersion: Int,
    mesh: ObjMesh?,
    mode: ViewMode,
    showWireframe: Boolean,
    onPaintPixel: (px: Float, py: Float) -> Unit,
    onStrokeEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val imageBitmap = remember(bitmap, paintVersion) { bitmap.asImageBitmap() }

    Canvas(
        modifier = modifier
            .pointerInput(mode) {
                if (mode == ViewMode.VIEW) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.25f, 8f)
                        offset += pan
                    }
                }
            }
            .pointerInput(mode, bitmap) {
                if (mode == ViewMode.PAINT) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val (px, py) = screenToTexture(pos, size.width.toFloat(), size.height.toFloat(), bitmap, scale, offset)
                            onPaintPixel(px, py)
                        },
                        onDrag = { change, _ ->
                            val (px, py) = screenToTexture(change.position, size.width.toFloat(), size.height.toFloat(), bitmap, scale, offset)
                            onPaintPixel(px, py)
                        },
                        onDragEnd = { onStrokeEnd() },
                        onDragCancel = { onStrokeEnd() }
                    )
                }
            }
    ) {
        val fitScale = kotlin.math.min(size.width / bitmap.width, size.height / bitmap.height)
        val drawScale = fitScale * scale
        val drawW = bitmap.width * drawScale
        val drawH = bitmap.height * drawScale
        val left = (size.width - drawW) / 2f + offset.x
        val top = (size.height - drawH) / 2f + offset.y

        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1))
        )

        if (showWireframe && mesh != null) {
            for (sub in mesh.subMeshes) {
                val idx = sub.indices
                var i = 0
                while (i + 2 < idx.size) {
                    val a = mesh.vertices[idx[i]].uv
                    val b = mesh.vertices[idx[i + 1]].uv
                    val c = mesh.vertices[idx[i + 2]].uv
                    fun toScreen(u: Float, v: Float) = Offset(left + u * drawW, top + (1f - v) * drawH)
                    val pa = toScreen(a.x, a.y); val pb = toScreen(b.x, b.y); val pc = toScreen(c.x, c.y)
                    drawLine(Color(0x99000000), pa, pb, 1f)
                    drawLine(Color(0x99000000), pb, pc, 1f)
                    drawLine(Color(0x99000000), pc, pa, 1f)
                    i += 3
                }
            }
        }
    }
}

private fun screenToTexture(
    pos: Offset, viewW: Float, viewH: Float, bitmap: Bitmap, scale: Float, offset: Offset
): Pair<Float, Float> {
    val fitScale = kotlin.math.min(viewW / bitmap.width, viewH / bitmap.height)
    val drawScale = fitScale * scale
    val drawW = bitmap.width * drawScale
    val drawH = bitmap.height * drawScale
    val left = (viewW - drawW) / 2f + offset.x
    val top = (viewH - drawH) / 2f + offset.y
    val px = (pos.x - left) / drawScale
    val py = (pos.y - top) / drawScale
    return px to py
}
