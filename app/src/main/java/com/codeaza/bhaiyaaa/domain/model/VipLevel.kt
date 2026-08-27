package com.codeaza.bhaiyaaa.domain.model

/**
 * VIP tiers, ordered by urgency. Stored in Room as the enum name so the
 * database stays readable and migration-friendly.
 */
enum class VipLevel(val storageValue: String, val label: String, val rank: Int) {
    NONE("NONE", "None", 0),
    VIP("VIP", "VIP", 1),
    SUPER_VIP("SUPER_VIP", "Super VIP", 2),
    EMERGENCY("EMERGENCY", "Emergency", 3);

    val isVip: Boolean get() = this != NONE

    companion object {
        /** Tiers a user can actually assign alert behaviour to. */
        val assignable: List<VipLevel> = listOf(VIP, SUPER_VIP, EMERGENCY)

        fun from(value: String?): VipLevel =
            entries.firstOrNull { it.storageValue == value } ?: NONE
    }
}
