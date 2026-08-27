package com.codeaza.bhaiyaaa.call

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object VipNotifier {
    const val CHANNEL_ID = "vip_calls"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VIP Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a VIP contact calls you"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyVipCall(context: Context, name: String, level: String) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val title = when (level) {
            "SUPER_VIP" -> "Boss, SUPER VIP call \uD83D\uDC40"
            "EMERGENCY" -> "\u26A0\uFE0F EMERGENCY caller"
            else -> "VIP caller"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText("$name is calling, bhai.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(name.hashCode(), notification)
    }
}
