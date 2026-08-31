package com.codeaza.bhaiyaaa.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale.
 *
 * Deliberately rounder than Material's defaults, which is the single clearest
 * signal of a current Android app versus one designed five years ago - M3
 * Expressive pushed corner radii up across the board. The steps are far apart
 * (8 / 14 / 20 / 28 / 36) so that nesting a chip inside a card inside a sheet
 * still reads as three distinct levels rather than one soft blur.
 */
val SukoonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** Fully rounded, for buttons and chips. */
val PillShape = RoundedCornerShape(percent = 50)

/** The standard card radius, used directly where a Shapes role would be vague. */
val CardShape = RoundedCornerShape(24.dp)
