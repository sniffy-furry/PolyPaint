package com.polypaint.app.paint

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import com.polypaint.app.model.MtlMaterial
import com.polypaint.app.model.PaintChannel
import java.io.File

/**
 * Owns the six paintable PBR channel bitmaps that back both the UV sheet
 * view and the live 3D preview. Painting on either view - and exporting -
 * all read/write the *same* bitmaps, so there's no separate "sync" step:
 * a stroke on the UV sheet and a stroke projected from the 3D view are
 * both just paintAt() calls into the same texture-pixel space.
 */
class TextureLayerManager(val resolution: Int = 1024) {

    private val bitmaps = LinkedHashMap<PaintChannel, Bitmap>()

    init {
        PaintChannel.values().forEach { ch -> bitmaps[ch] = makeDefaultBitmap(ch) }
    }

    fun bitmapFor(channel: PaintChannel): Bitmap = bitmaps.getValue(channel)
    fun allBitmaps(): Map<PaintChannel, Bitmap> = bitmaps

    private fun makeDefaultBitmap(channel: PaintChannel): Bitmap {
        val bmp = Bitmap.createBitmap(resolution, resolution, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(channel.defaultColor)
        return bmp
    }

    /** Loads a material's maps in place of the defaults where present, scaling to [resolution]. */
    fun loadFromMaterial(material: MtlMaterial, baseDir: File) {
        fun tryLoad(path: String?, channel: PaintChannel) {
            if (path == null) return
            val file = File(baseDir, path)
            if (!file.exists()) return
            val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val scaled = if (decoded.width != resolution || decoded.height != resolution) {
                Bitmap.createScaledBitmap(decoded, resolution, resolution, true)
            } else decoded
            bitmaps[channel] = scaled.copy(Bitmap.Config.ARGB_8888, true)
        }
        tryLoad(material.albedoMapPath, PaintChannel.ALBEDO)
        tryLoad(material.normalMapPath, PaintChannel.NORMAL)
        tryLoad(material.roughnessMapPath, PaintChannel.ROUGHNESS)
        tryLoad(material.metallicMapPath, PaintChannel.METALLIC)
        tryLoad(material.aoMapPath, PaintChannel.AO)
        tryLoad(material.emissiveMapPath, PaintChannel.EMISSIVE)
    }

    /** Stamps [brush] centered at UV coordinate (u, v), 0..1, V measured
     *  from the bottom like the standard OBJ/UV convention. */
    fun paintAt(channel: PaintChannel, u: Float, v: Float, brush: Brush): Rect? {
        val bmp = bitmaps.getValue(channel)
        val px = u * bmp.width
        val py = (1f - v) * bmp.height
        return stamp(bmp, px, py, brush, channel.isScalar)
    }

    /** Stamps directly in bitmap pixel space - used by the UV sheet view. */
    fun paintAtPixel(channel: PaintChannel, px: Float, py: Float, brush: Brush): Rect? {
        val bmp = bitmaps.getValue(channel)
        return stamp(bmp, px, py, brush, channel.isScalar)
    }

    private fun stamp(bmp: Bitmap, px: Float, py: Float, brush: Brush, scalar: Boolean): Rect? {
        val r = brush.radiusPx
        if (px < -r || py < -r || px > bmp.width + r || py > bmp.height + r) return null

        val canvas = Canvas(bmp)
        val baseColor = if (scalar) {
            val g = (brush.scalarValue.coerceIn(0f, 1f) * 255).toInt()
            Color.argb((brush.opacity.coerceIn(0f, 1f) * 255).toInt(), g, g, g)
        } else {
            val a = (brush.opacity.coerceIn(0f, 1f) * 255).toInt()
            Color.argb(a, Color.red(brush.colorArgb), Color.green(brush.colorArgb), Color.blue(brush.colorArgb))
        }
        val transparent = Color.argb(0, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val hardStop = brush.hardness.coerceIn(0f, 0.98f)
        paint.shader = RadialGradient(
            px, py, r,
            intArrayOf(baseColor, baseColor, transparent),
            floatArrayOf(0f, hardStop, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(px, py, r, paint)

        val left = (px - r).toInt().coerceIn(0, bmp.width)
        val top = (py - r).toInt().coerceIn(0, bmp.height)
        val right = (px + r).toInt().coerceIn(0, bmp.width)
        val bottom = (py + r).toInt().coerceIn(0, bmp.height)
        return Rect(left, top, right, bottom)
    }

    fun reset(channel: PaintChannel) {
        bitmaps[channel] = makeDefaultBitmap(channel)
    }
}
