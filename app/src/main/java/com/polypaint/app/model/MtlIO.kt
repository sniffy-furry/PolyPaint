package com.polypaint.app.model

import java.io.File

object MtlIO {

    fun read(mtlFile: File): Map<String, MtlMaterial> {
        val materials = LinkedHashMap<String, MtlMaterial>()
        var current: MtlMaterial? = null
        mtlFile.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val tokens = line.split(Regex("\\s+"))
            when (tokens[0]) {
                "newmtl" -> {
                    val name = tokens.getOrNull(1) ?: return@forEachLine
                    current = MtlMaterial(name).also { materials[name] = it }
                }
                "Kd" -> if (tokens.size >= 4) {
                    current?.baseColor = Vec3(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())
                }
                "Pr" -> current?.let { it.roughness = tokens.getOrNull(1)?.toFloatOrNull() ?: it.roughness }
                "Pm" -> current?.let { it.metallic = tokens.getOrNull(1)?.toFloatOrNull() ?: it.metallic }
                "map_Kd" -> current?.albedoMapPath = tokens.lastOrNull()
                "map_Bump", "bump", "norm", "map_norm" -> current?.normalMapPath = tokens.lastOrNull()
                "map_Pr" -> current?.roughnessMapPath = tokens.lastOrNull()
                "map_Pm" -> current?.metallicMapPath = tokens.lastOrNull()
                "map_Ka" -> current?.aoMapPath = tokens.lastOrNull()
                "map_Ke" -> current?.emissiveMapPath = tokens.lastOrNull()
                else -> Unit
            }
        }
        return materials
    }

    /**
     * Writes a .mtl using the map_Pr / map_Pm PBR extension lines most
     * modern importers (Blender included) understand. There's no single
     * universal "ambient occlusion" tag in the classic MTL spec, so map_Ka
     * is reused for it, matching common practice; map_Ke carries emissive.
     */
    fun write(materialName: String, textureFileNames: Map<PaintChannel, String>, out: File) {
        out.bufferedWriter().use { w ->
            w.appendLine("# Exported by PolyPaint")
            w.appendLine("newmtl $materialName")
            w.appendLine("Kd 1.000 1.000 1.000")
            w.appendLine("Pr 1.000")
            w.appendLine("Pm 1.000")
            w.appendLine("illum 2")
            textureFileNames[PaintChannel.ALBEDO]?.let { w.appendLine("map_Kd $it") }
            textureFileNames[PaintChannel.NORMAL]?.let {
                w.appendLine("map_Bump $it")
                w.appendLine("norm $it")
            }
            textureFileNames[PaintChannel.ROUGHNESS]?.let { w.appendLine("map_Pr $it") }
            textureFileNames[PaintChannel.METALLIC]?.let { w.appendLine("map_Pm $it") }
            textureFileNames[PaintChannel.AO]?.let { w.appendLine("map_Ka $it") }
            textureFileNames[PaintChannel.EMISSIVE]?.let { w.appendLine("map_Ke $it") }
        }
    }
}
