package com.codeaza.bhaiyaaa.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.ui.graphics.vector.ImageVector
import com.codeaza.bhaiyaaa.R
import com.codeaza.bhaiyaaa.ui.prayer.QuietTimesFocus

/**
 * Every route in the app.
 *
 * Five destinations sit in the bottom bar - the ceiling before a Material 3
 * navigation bar starts truncating labels. The remaining four top-level screens
 * (VIP, Memory, Insights, Settings) live behind "More", which keeps the bar
 * readable without hiding anything more than one tap deep.
 */
object Routes {

    const val ARG_FOCUS = "focus"

    const val ONBOARDING = "onboarding"

    const val HOME = "home"
    const val CALLS = "calls"
    const val CONTACTS = "contacts"
    const val ASSISTANT = "assistant"
    const val MORE = "more"

    const val VIP = "vip"
    const val MEMORY = "memory"
    const val INSIGHTS = "insights"
    const val SEARCH = "search"
    const val REMINDERS = "reminders"

    const val SETTINGS = "settings"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_VIP_ALERTS = "settings/vip-alerts"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_PERSONALITY = "settings/personality"
    const val SETTINGS_SECURITY = "settings/security"
    const val SETTINGS_DATA = "settings/data"
    /**
     * Quiet times.
     *
     * Carries an optional focus so a caller can say what it is sending the
     * user there to do - the dashboard's "Set prayer times" button means
     * something different from Settings → Quiet times, and the screen
     * reorders itself accordingly. Optional rather than required so the plain
     * route keeps working and nothing already linking here had to change.
     */
    private const val PRAYER_BASE = "settings/prayer"
    const val SETTINGS_PRAYER = "$PRAYER_BASE?focus={$ARG_FOCUS}"

    fun prayerSettings(focus: QuietTimesFocus = QuietTimesFocus.NONE): String =
        "$PRAYER_BASE?focus=${focus.name}"

    const val SETTINGS_RECORDINGS = "settings/recordings"
    const val SETTINGS_MODELS = "settings/models"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_LICENCES = "settings/licences"
    const val PRIVACY_CENTER = "privacy-center"

    private const val CONTACT_BASE = "contact"
    const val CONTACT_DETAIL = "$CONTACT_BASE/{phoneNumber}"
    fun contactDetail(phoneNumber: String): String = "$CONTACT_BASE/${Uri.encode(phoneNumber)}"

    private const val CALL_BASE = "call"
    const val CALL_DETAIL = "$CALL_BASE/{callId}"
    fun callDetail(callId: Long): String = "$CALL_BASE/$callId"

    const val ARG_PHONE_NUMBER = "phoneNumber"
    const val ARG_CALL_ID = "callId"
}

/** A tab in the bottom navigation bar. */
enum class BottomDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    CALLS(Routes.CALLS, R.string.nav_calls, Icons.Filled.Call, Icons.Outlined.Call),
    CONTACTS(Routes.CONTACTS, R.string.nav_contacts, Icons.Filled.People, Icons.Outlined.People),
    ASSISTANT(
        Routes.ASSISTANT,
        R.string.nav_assistant,
        Icons.AutoMirrored.Filled.Chat,
        Icons.AutoMirrored.Filled.Chat
    ),
    MORE(Routes.MORE, R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz);

    companion object {
        /** Routes that should show the bottom bar. Detail screens hide it. */
        val routes: Set<String> = entries.map { it.route }.toSet()
    }
}
