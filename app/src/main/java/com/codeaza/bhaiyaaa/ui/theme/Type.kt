package com.codeaza.bhaiyaaa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.codeaza.bhaiyaaa.R

/**
 * Manrope, bundled rather than downloaded.
 *
 * Chosen over the platform default because Roboto is what every Android app
 * looks like by default, and BHAIYAAA should not. Manrope is a semi-geometric
 * grotesque: open counters keep names and numbers legible at list sizes, while
 * its heavier weights have enough personality to carry a screen title without a
 * second display face - so the whole app runs on one family at four weights,
 * which is ~390 KB rather than a megabyte.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
)

/**
 * Trims the extra leading Compose adds above the first line and below the last,
 * so a heading sits flush against the container it's in. Without this, generous
 * line heights push text off-centre inside buttons and cards.
 */
private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

/**
 * The type scale.
 *
 * Material 3 Expressive leans on weight contrast rather than size alone, so the
 * jump from body to display here is 400 -> 800, not just 14sp -> 34sp. Display
 * sizes carry negative tracking because large text set at default tracking
 * reads loose and dated; body sizes keep positive tracking for legibility at a
 * glance. Every size is in sp, so it all scales with the user's font setting.
 */
val BhaiyaaaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.2).sp,
        lineHeightStyle = Trim
    ),
    displayMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.0).sp,
        lineHeightStyle = Trim
    ),
    displaySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.7).sp,
        lineHeightStyle = Trim
    ),
    headlineLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.6).sp,
        lineHeightStyle = Trim
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = Trim
    ),
    headlineSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = Trim
    ),
    titleLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = Trim
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = Trim
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = Trim
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = Trim
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.15.sp,
        lineHeightStyle = Trim
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        lineHeightStyle = Trim
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = Trim
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        lineHeightStyle = Trim
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope,
        // Tier badges and overlines are set in this style at all-caps, where
        // open tracking is what stops small capitals reading as a smudge.
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.9.sp,
        lineHeightStyle = Trim
    )
)

/**
 * Numeric style for stats, durations and counts.
 *
 * Tabular figures so numbers in a column line up: without `tnum` a "1" is
 * narrower than a "7", and the Insights tiles visibly jitter as values change.
 */
val NumericTextStyle = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.ExtraBold,
    fontFeatureSettings = "tnum"
)
