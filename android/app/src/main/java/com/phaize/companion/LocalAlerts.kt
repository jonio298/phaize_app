package com.phaize.companion

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Foreground best-effort alerts only. These are not FCM/background push.
 * No agent messages, paths, prompts or log details appear on the lock screen.
 */
object LocalAlerts {
 private const val CHANNEL="phaize_security_alerts"
 fun createChannel(context:Context) {
  val manager=context.getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel(CHANNEL,"PhAize security alerts",NotificationManager.IMPORTANCE_HIGH).apply{
   description="Opt-in urgent PhAize event notifications"
   lockscreenVisibility=Notification.VISIBILITY_PRIVATE
  })
 }
 fun permitted(context:Context):Boolean =
  Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED

 fun notify(context:Context,alert:AlertItem) {
  if(!permitted(context) || alert.severity.lowercase() !in setOf("high","critical"))return
  val notification=Notification.Builder(context,CHANNEL)
   .setSmallIcon(android.R.drawable.stat_notify_error)
   .setContentTitle("PhAize security alert")
   .setContentText("Open PhAize to review an important desktop event.")
   .setCategory(Notification.CATEGORY_ALARM)
   .setVisibility(Notification.VISIBILITY_PRIVATE)
   .setAutoCancel(true)
   .build()
  context.getSystemService(NotificationManager::class.java).notify(alert.id.hashCode(),notification)
 }
}
