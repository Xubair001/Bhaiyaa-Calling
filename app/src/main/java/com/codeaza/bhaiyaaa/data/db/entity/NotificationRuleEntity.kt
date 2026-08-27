package com.codeaza.bhaiyaaa.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-VIP-tier alert behaviour. One row per assignable tier
 * (VIP / SUPER_VIP / EMERGENCY), seeded with sensible defaults and fully
 * editable from Settings.
 *
 * `vibrationPatternCsv` is a comma-separated millisecond wave (off,on,off,on…)
 * matching the contract of VibrationEffect.createWaveform.
 */
@Entity(tableName = "notification_rules")
data class NotificationRuleEntity(
    @PrimaryKey val vipLevel: String,
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val vibrationPatternCsv: String = "0,400,200,400",
    val flashEnabled: Boolean = true,
    val flashCount: Int = 3,
    val flashOnMillis: Long = 180,
    val flashOffMillis: Long = 180,
    val customSoundUri: String? = null,
    /** Only takes effect if the user has granted DND override in system settings. */
    val bypassDnd: Boolean = false,
    /**
     * Whether this tier still reaches the user during a prayer silence window.
     * Off for VIP and Super VIP, on for Emergency, and changeable per tier.
     */
    @ColumnInfo(defaultValue = "0")
    val ringsDuringPrayer: Boolean = false
) {
    companion object {
        /**
         * The shipped defaults for a tier.
         *
         * Single source of truth, used for seeding, for repairing a missing row,
         * and as the fallback when a rule cannot be read. A tier with no row is
         * a seeding failure, not a user switching alerts off, and treating the
         * two the same silently kills that tier - which is exactly what happened.
         */
        fun defaultFor(vipLevel: String): NotificationRuleEntity = when (vipLevel) {
            "SUPER_VIP" -> NotificationRuleEntity(
                vipLevel = vipLevel,
                vibrationPatternCsv = "0,500,150,500,150,500",
                flashCount = 5
            )
            "EMERGENCY" -> NotificationRuleEntity(
                vipLevel = vipLevel,
                vibrationPatternCsv = "0,400,150,400,150,400,150,400,150,400",
                flashCount = 8,
                flashOnMillis = 140,
                flashOffMillis = 140,
                // Prayer outranks the other tiers, but not a genuine emergency.
                ringsDuringPrayer = true
            )
            else -> NotificationRuleEntity(
                vipLevel = vipLevel,
                vibrationPatternCsv = "0,400,200,400",
                flashCount = 3
            )
        }

        fun allDefaults(): List<NotificationRuleEntity> =
            listOf("VIP", "SUPER_VIP", "EMERGENCY").map { defaultFor(it) }
    }
}
