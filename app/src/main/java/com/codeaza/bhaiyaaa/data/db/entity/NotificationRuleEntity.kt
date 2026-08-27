package com.codeaza.bhaiyaaa.data.db.entity

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
    val bypassDnd: Boolean = false
)
