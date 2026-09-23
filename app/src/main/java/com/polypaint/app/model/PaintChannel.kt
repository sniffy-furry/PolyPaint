package com.polypaint.app.model

import android.graphics.Color

/**
 * The PBR texture channels PolyPaint can paint into. Every channel is a
 * full ARGB_8888 bitmap for simplicity, even the scalar ones (roughness /
 * metallic / occlusion), which just use their red channel - packing them
 * into one combined ORM texture would be a nice follow-up optimization.
 */
enum class PaintChannel(val displayName: String, val isScalar: Boolean, val defaultColor: Int) {
    ALBEDO("Albedo", isScalar = false, defaultColor = Color.argb(255, 180, 180, 180)),
    NORMAL("Normal", isScalar = false, defaultColor = Color.argb(255, 128, 128, 255)),
    ROUGHNESS("Roughness", isScalar = true, defaultColor = Color.argb(255, 153, 153, 153)),
    METALLIC("Metallic", isScalar = true, defaultColor = Color.argb(255, 0, 0, 0)),
    AO("Occlusion", isScalar = true, defaultColor = Color.argb(255, 255, 255, 255)),
    EMISSIVE("Emissive", isScalar = false, defaultColor = Color.argb(255, 0, 0, 0))
}
