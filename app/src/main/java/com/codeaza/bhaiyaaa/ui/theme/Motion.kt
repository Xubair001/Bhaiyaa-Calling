package com.codeaza.bhaiyaaa.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * The app's motion spec.
 *
 * Timings live here rather than as numbers scattered through screens, so
 * motion reads as one system instead of each screen having its own feel.
 *
 * Nothing here scales for accessibility, and it does not need to: Compose
 * already multiplies every animation by the platform's animator duration
 * scale, so "Remove animations" in system settings turns these off without
 * the app checking anything.
 */
object Motion {

    /** State flips - a checkbox, a chip, a colour change. Barely perceptible. */
    const val QUICK = 140

    /** The default for anything that changes layout. */
    const val STANDARD = 240

    /**
     * Screen entry. Longer than the matching exit, because the thing arriving
     * is what the user is looking for; the thing leaving should get out of the
     * way rather than be watched.
     */
    const val SCREEN_ENTER = 300
    const val SCREEN_EXIT = 200

    /**
     * Decelerating: fast at the start, settling at the end. Everything that
     * arrives or expands uses this - it reads as the element coming to rest
     * rather than braking.
     */
    val Entering: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Accelerating, for things on their way out. */
    val Leaving: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Symmetric, for a change that is neither an arrival nor a departure. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
