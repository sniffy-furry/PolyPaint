package com.polypaint.app.io

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {

    fun extract(zipFile: File, destDir: File) {
        destDir.mkdirs()
        val destCanonical = destDir.canonicalPath
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zin ->
            var entry: ZipEntry? = zin.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                val outCanonical = outFile.canonicalPath
                // Guard against zip-slip path traversal from a malicious entry name.
                if (outCanonical != destCanonical && !outCanonical.startsWith(destCanonical + File.separator)) {
                    zin.closeEntry(); entry = zin.nextEntry; continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zin.copyTo(out) }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
    }

    fun zipDirectory(sourceDir: File, outZip: File) {
        ZipOutputStream(FileOutputStream(outZip)).use { zout ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relPath = file.relativeTo(sourceDir).path
                zout.putNextEntry(ZipEntry(relPath))
                FileInputStream(file).use { it.copyTo(zout) }
                zout.closeEntry()
            }
        }
    }
}
