package com.polypaint.app.paint

data class Brush(
    val radiusPx: Float = 28f,
    val hardness: Float = 0.6f,    // 0 = very soft falloff, ~1 = hard edge
    val opacity: Float = 1f,       // 0..1 alpha applied per stamp
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val scalarValue: Float = 0.5f  // used for scalar channels (roughness/metallic/AO)
)
