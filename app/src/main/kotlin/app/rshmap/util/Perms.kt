package app.rshmap.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityManager
import app.rshmap.service.PadAccessibilityService

object Perms {
    fun canOverlay(c: Context) = AndroidSettings.canDrawOverlays(c)

    fun overlayIntent(c: Context) =
        Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))

    fun a11yEnabled(c: Context): Boolean {
        val am = c.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val me = ComponentName(c, PadAccessibilityService::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC).any {
            val si = it.resolveInfo.serviceInfo
            ComponentName(si.packageName, si.name) == me
        }
    }

    fun a11yIntent() = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)

    fun batteryOptimizationIgnored(c: Context): Boolean =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)

    fun batteryIntent(c: Context) =
        Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))

    fun notificationsEnabled(c: Context): Boolean =
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
}
