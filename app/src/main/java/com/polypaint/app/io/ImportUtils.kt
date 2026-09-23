package com.polypaint.app.io

import android.content.Context
import android.net.Uri
import java.io.File

object ImportUtils {

    /**
     * Copies whatever the user picked (a single .obj, or a .zip bundle
     * containing .obj + .mtl + textures) into app-private cache storage and
     * returns the resolved .obj File to load.
     *
     * Why: a raw SAF content:// Uri for one picked document grants access
     * to *that document only* - there's no way to open sibling files (the
     * .mtl next to it, its texture images) from it directly. Staging into
     * a real local folder first means ObjLoader/MtlIO can resolve those
     * sibling references with plain File I/O, no extra permissions needed.
     */
    fun stageImport(context: Context, pickedUri: Uri, displayName: String?): File? {
        val stagingDir = File(context.cacheDir, "import").also {
            it.deleteRecursively(); it.mkdirs()
        }
        val name = displayName ?: "import"
        val lower = name.lowercase()

        return if (lower.endsWith(".zip")) {
            val tempZip = File(context.cacheDir, "incoming.zip")
            context.contentResolver.openInputStream(pickedUri)?.use { input ->
                tempZip.outputStream().use { output -> input.copyTo(output) }
            }
            ZipUtils.extract(tempZip, stagingDir)
            tempZip.delete()
            stagingDir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("obj", ignoreCase = true) }
        } else {
            val objFile = File(stagingDir, if (lower.endsWith(".obj")) name else "$name.obj")
            context.contentResolver.openInputStream(pickedUri)?.use { input ->
                objFile.outputStream().use { output -> input.copyTo(output) }
            }
            objFile
        }
    }
}
