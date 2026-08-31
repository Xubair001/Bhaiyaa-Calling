package com.codeaza.bhaiyaaa.domain.model

/**
 * Where a memory came from. This matters for honesty: Sukoon must never
 * present something it inferred as something it was told, so every memory
 * carries its provenance and the UI shows it.
 */
enum class MemorySource(val storageValue: String, val label: String) {
    /** Typed by the user. */
    MANUAL("MANUAL", "Note"),
    /** Typed by the user against a specific call in the log. */
    CALL_NOTE("CALL_NOTE", "Call note"),
    /** Captured from an Assistant conversation at the user's request. */
    ASSISTANT("ASSISTANT", "From assistant"),
    /** A task the user extracted from a note. */
    ACTION_ITEM("ACTION_ITEM", "Action item");

    companion object {
        fun from(value: String?): MemorySource =
            entries.firstOrNull { it.storageValue == value } ?: MANUAL
    }
}
