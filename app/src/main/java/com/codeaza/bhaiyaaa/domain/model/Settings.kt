package com.codeaza.bhaiyaaa.domain.model

/** How BHAIYAAA talks. Affects wording only - never what the data says. */
enum class PersonalityMode(val storageValue: String, val label: String, val description: String) {
    PROFESSIONAL("PROFESSIONAL", "Professional", "Plain, neutral wording."),
    FRIENDLY("FRIENDLY", "Friendly", "Warm and casual, light emoji."),
    BHAI("BHAI", "Bhai Mode", "Full desi bhai energy. Chill, short, a bit funny.");

    companion object {
        fun from(value: String?): PersonalityMode =
            entries.firstOrNull { it.storageValue == value } ?: FRIENDLY
    }
}

enum class ThemeMode(val storageValue: String, val label: String) {
    SYSTEM("SYSTEM", "Follow system"),
    LIGHT("LIGHT", "Light"),
    DARK("DARK", "Dark");

    companion object {
        fun from(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/** Everything in one snapshot so Compose can key off a single state object. */
data class AppSettings(
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val personality: PersonalityMode = PersonalityMode.FRIENDLY,
    val notificationsEnabled: Boolean = true,
    val flashlightEnabled: Boolean = true,
    val missedCallNudgeEnabled: Boolean = true,
    val autoSyncEnabled: Boolean = true,
    val privateMemoriesHidden: Boolean = true,
    val lastSyncAt: Long = 0L,
    val prayer: PrayerSettings = PrayerSettings()
)
