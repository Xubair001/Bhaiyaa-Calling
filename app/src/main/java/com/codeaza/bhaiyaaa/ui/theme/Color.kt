package com.codeaza.bhaiyaaa.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * BHAIYAAA's palette: royal violet, with rose and periwinkle supporting it.
 *
 * Violet does something no other hue here could. This app's whole job is
 * private: a VIP list you don't want on show, notes about people, a lock over
 * the lot. Violet reads as after-dark and closed-doors rather than as a
 * utility, which is the register the product is actually in - and it stays
 * clear of blue, which is what every other phone tool defaults to.
 *
 * The three accents are far enough apart in hue that the VIP tiers escalate
 * periwinkle -> lilac -> coral and read as ranked without a legend, but close
 * enough to sit together without any one of them shouting.
 *
 * Neutrals are violet-tinted rather than grey, so dark mode reads like a dim
 * room instead of the cold slate that makes most dark themes feel like a
 * terminal.
 */

// ---------------------------------------------------------------- light theme

val VioletPrimaryLight = Color(0xFF6A3FBF)
val VioletOnPrimaryLight = Color(0xFFFFFFFF)
val VioletContainerLight = Color(0xFFEADDFF)
val VioletOnContainerLight = Color(0xFF230A54)

val RoseSecondaryLight = Color(0xFF8B4A72)
val RoseOnSecondaryLight = Color(0xFFFFFFFF)
val RoseContainerLight = Color(0xFFFFD8EC)
val RoseOnContainerLight = Color(0xFF38072A)

val PeriwinkleTertiaryLight = Color(0xFF5546A8)
val PeriwinkleOnTertiaryLight = Color(0xFFFFFFFF)
val PeriwinkleContainerLight = Color(0xFFE4DFFF)
val PeriwinkleOnTertiaryContainerLight = Color(0xFF160164)

val BackgroundLight = Color(0xFFFDF7FF)
val OnBackgroundLight = Color(0xFF1D1A22)
val SurfaceVariantLight = Color(0xFFE7E0EB)
val OnSurfaceVariantLight = Color(0xFF49454E)
val OutlineLight = Color(0xFF7A757F)

val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410E0B)

// ----------------------------------------------------------------- dark theme

val VioletPrimaryDark = Color(0xFFC9A7FF)
val VioletOnPrimaryDark = Color(0xFF3A1D6E)
val VioletContainerDark = Color(0xFF513492)
val VioletOnContainerDark = Color(0xFFEADDFF)

val RoseSecondaryDark = Color(0xFFF2B8D8)
val RoseOnSecondaryDark = Color(0xFF4B2540)
val RoseContainerDark = Color(0xFF653B57)
val RoseOnContainerDark = Color(0xFFFFD8EC)

val PeriwinkleTertiaryDark = Color(0xFF9D8DF1)
val PeriwinkleOnTertiaryDark = Color(0xFF2E2168)
val PeriwinkleContainerDark = Color(0xFF453880)
val PeriwinkleOnTertiaryContainerDark = Color(0xFFE4DFFF)

val BackgroundDark = Color(0xFF121016)
val OnBackgroundDark = Color(0xFFE7E0E8)
val SurfaceVariantDark = Color(0xFF49454F)
val OnSurfaceVariantDark = Color(0xFFCAC4CF)
val OutlineDark = Color(0xFF948F99)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

/**
 * Raised surfaces, tinted toward the primary by hand.
 *
 * Material's automatic elevation tint is too subtle at the low elevations this
 * app uses - a card would barely separate from the page behind it.
 */
val SurfaceContainerLight = Color(0xFFF4EDF9)
val SurfaceContainerHighLight = Color(0xFFEEE6F4)
val SurfaceContainerDark = Color(0xFF1D1A23)
val SurfaceContainerHighDark = Color(0xFF282430)
