package com.polypaint.app.io

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.polypaint.app.model.MtlIO
import com.polypaint.app.model.ObjMesh
import com.polypaint.app.model.PaintChannel
import com.polypaint.app.paint.TextureLayerManager
import java.io.File
import java.io.FileOutputStream

/**
 * Writes model.obj + model.mtl + one PNG per painted channel into a fresh
 * folder, zips it, and copies the zip to a SAF-picked destination.
 *
 * A .obj file can't literally embed image data - textures are always
 * separate files referenced by the .mtl. This zip (obj + mtl + PNGs
 * together) is the closest single-file equivalent to "the model with its
 * textures inside it" while keeping the actual .obj/.mtl fully standard
 * and compatible with any 3D tool.
 */
object ModelExporter {

    private const val MATERIAL_NAME = "polypaint_material"

    fun export(context: Context, mesh: ObjMesh, textures: TextureLayerManager, outUri: Uri) {
        val exportDir = File(context.cacheDir, "export").also { it.deleteRecursively(); it.mkdirs() }

        val texFileNames = LinkedHashMap<PaintChannel, String>()
        for (channel in PaintChannel.values()) {
            val bmp = textures.bitmapFor(channel)
            if (!isBitmapDefault(bmp, channel)) {
                val fileName = "${channel.name.lowercase()}.png"
                File(exportDir, fileName).outputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                texFileNames[channel] = fileName
            }
        }

        val materialName = if (texFileNames.isEmpty()) null else MATERIAL_NAME
        if (materialName != null) {
            MtlIO.write(materialName, texFileNames, File(exportDir, "model.mtl"))
        }
        writeObj(mesh, File(exportDir, "model.obj"), "model.mtl", materialName)

        val zipFile = File(context.cacheDir, "polypaint_export.zip")
        ZipUtils.zipDirectory(exportDir, zipFile)

        context.contentResolver.openOutputStream(outUri)?.use { out ->
            zipFile.inputStream().use { input -> input.copyTo(out) }
        }
    }

    /** Cheap heuristic: sample a handful of pixels; skip exporting a PNG
     *  for a channel that was never actually painted on. */
    private fun isBitmapDefault(bmp: Bitmap, channel: PaintChannel): Boolean {
        val samplePoints = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        for (fx in samplePoints) for (fy in samplePoints) {
            val x = (fx * (bmp.width - 1)).toInt()
            val y = (fy * (bmp.height - 1)).toInt()
            if (bmp.getPixel(x, y) != channel.defaultColor) return false
        }
        return true
    }

    private fun writeObj(mesh: ObjMesh, outFile: File, mtlFileName: String, materialName: String?) {
        FileOutputStream(outFile).bufferedWriter().use { w ->
            w.appendLine("# Exported by PolyPaint")
            if (materialName != null) w.appendLine("mtllib $mtlFileName")
            for (v in mesh.vertices) w.appendLine("v ${v.position.x} ${v.position.y} ${v.position.z}")
            for (v in mesh.vertices) w.appendLine("vt ${v.uv.x} ${v.uv.y}")
            for (v in mesh.vertices) w.appendLine("vn ${v.normal.x} ${v.normal.y} ${v.normal.z}")
            if (materialName != null) w.appendLine("usemtl $materialName")
            for (sub in mesh.subMeshes) {
                val idx = sub.indices
                var i = 0
                while (i + 2 < idx.size) {
                    val a = idx[i] + 1; val b = idx[i + 1] + 1; val c = idx[i + 2] + 1
                    w.appendLine("f $a/$a/$a $b/$b/$b $c/$c/$c")
                    i += 3
                }
            }
        }
    }
}
