package com.codeaza.bhaiyaaa.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * BHAIYAAA's palette: deep saffron, terracotta and plum over warm neutrals.
 *
 * Why this and not the obvious options. Blue is the default of every utility
 * app and says nothing; green is what phone apps already use for the call
 * button, so it would fight the content. Saffron carries warmth and a South
 * Asian register without resorting to flag colours, and it has a property no
 * other hue here has: gold already means "important" to everyone, so the VIP
 * tiers can escalate plum -> saffron -> vermilion and read as ranked without a
 * legend.
 *
 * The neutrals are warm rather than grey - every surface is tinted toward the
 * primary's hue, so the dark theme reads like dim lamplight instead of the cold
 * slate that makes so many dark modes feel like a terminal.
 */

// ---------------------------------------------------------------- light theme

val SaffronPrimaryLight = Color(0xFF8A5000)
val SaffronOnPrimaryLight = Color(0xFFFFFFFF)
val SaffronContainerLight = Color(0xFFFFDCBB)
val SaffronOnContainerLight = Color(0xFF2C1600)

val TerracottaSecondaryLight = Color(0xFF8F4B36)
val TerracottaOnSecondaryLight = Color(0xFFFFFFFF)
val TerracottaContainerLight = Color(0xFFFFDBCF)
val TerracottaOnContainerLight = Color(0xFF380D01)

val PlumTertiaryLight = Color(0xFF7A4A72)
val PlumOnTertiaryLight = Color(0xFFFFFFFF)
val PlumContainerLight = Color(0xFFFFD6F4)
val PlumOnTertiaryContainerLight = Color(0xFF30062C)

val BackgroundLight = Color(0xFFFFF8F4)
val OnBackgroundLight = Color(0xFF211A15)
val SurfaceVariantLight = Color(0xFFF2DFD1)
val OnSurfaceVariantLight = Color(0xFF51443A)
val OutlineLight = Color(0xFF847469)

val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD5)
val OnErrorContainerLight = Color(0xFF410E06)

// ----------------------------------------------------------------- dark theme

val SaffronPrimaryDark = Color(0xFFFFB865)
val SaffronOnPrimaryDark = Color(0xFF492900)
val SaffronContainerDark = Color(0xFF693C00)
val SaffronOnContainerDark = Color(0xFFFFDCBB)

val TerracottaSecondaryDark = Color(0xFFFFB59D)
val TerracottaOnSecondaryDark = Color(0xFF54200D)
val TerracottaContainerDark = Color(0xFF723521)
val TerracottaOnContainerDark = Color(0xFFFFDBCF)

val PlumTertiaryDark = Color(0xFFEAB1DE)
val PlumOnTertiaryDark = Color(0xFF471D42)
val PlumContainerDark = Color(0xFF603359)
val PlumOnTertiaryContainerDark = Color(0xFFFFD6F4)

val BackgroundDark = Color(0xFF19120C)
val OnBackgroundDark = Color(0xFFEFE0D6)
val SurfaceVariantDark = Color(0xFF51443A)
val OnSurfaceVariantDark = Color(0xFFD5C3B5)
val OutlineDark = Color(0xFF9D8D81)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD5)

/**
 * Raised surfaces.
 *
 * Material tints elevated surfaces toward the primary. Doing it explicitly here
 * keeps cards legible against the warm background at the low elevations this
 * app uses, where the default tint is too subtle to separate a card from the
 * page behind it.
 */
val SurfaceContainerLight = Color(0xFFFBEDE2)
val SurfaceContainerHighLight = Color(0xFFF6E7DC)
val SurfaceContainerDark = Color(0xFF261D16)
val SurfaceContainerHighDark = Color(0xFF32271F)
