package com.codeaza.bhaiyaaa.domain.model

/**
 * Built-in categories. Users can also create their own tags, which are stored
 * in the `tags` table - these are just the ones Sukoon ships with so the
 * picker is never empty on first run.
 */
object ContactTag {
    const val FAMILY = "Family"
    const val FRIENDS = "Friends"
    const val WORK = "Work"
    const val CLIENT = "Client"
    const val IMPORTANT = "Important"
    const val UNKNOWN = "Unknown"
    const val SPAM = "Spam"

    val BUILT_IN = listOf(FAMILY, FRIENDS, WORK, CLIENT, IMPORTANT, UNKNOWN, SPAM)
}

/** How much a contact matters. Drives sorting and the "important" call filter. */
enum class Importance(val storageValue: Int, val label: String) {
    LOW(0, "Low"),
    NORMAL(1, "Normal"),
    HIGH(2, "High"),
    CRITICAL(3, "Critical");

    companion object {
        fun from(value: Int?): Importance =
            entries.firstOrNull { it.storageValue == value } ?: NORMAL
    }
}
