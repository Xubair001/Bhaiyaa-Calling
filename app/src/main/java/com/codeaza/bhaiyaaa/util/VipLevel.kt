package com.codeaza.bhaiyaaa.util

object VipLevel {
    const val NONE = "NONE"
    const val VIP = "VIP"
    const val SUPER_VIP = "SUPER_VIP"
    const val EMERGENCY = "EMERGENCY"

    val ALL = listOf(NONE, VIP, SUPER_VIP, EMERGENCY)

    fun label(level: String): String = when (level) {
        VIP -> "VIP"
        SUPER_VIP -> "Super VIP"
        EMERGENCY -> "Emergency"
        else -> "None"
    }
}
