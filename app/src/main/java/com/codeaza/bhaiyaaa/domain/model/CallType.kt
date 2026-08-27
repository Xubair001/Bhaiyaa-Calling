package com.codeaza.bhaiyaaa.domain.model

enum class CallType(val storageValue: String, val label: String) {
    INCOMING("INCOMING", "Incoming"),
    OUTGOING("OUTGOING", "Outgoing"),
    MISSED("MISSED", "Missed"),
    REJECTED("REJECTED", "Rejected"),
    BLOCKED("BLOCKED", "Blocked"),
    VOICEMAIL("VOICEMAIL", "Voicemail"),
    OTHER("OTHER", "Other");

    /** A call that never connected - nothing was said, so no duration is meaningful. */
    val isUnanswered: Boolean get() = this == MISSED || this == REJECTED || this == BLOCKED

    companion object {
        fun from(value: String?): CallType =
            entries.firstOrNull { it.storageValue == value } ?: OTHER
    }
}
