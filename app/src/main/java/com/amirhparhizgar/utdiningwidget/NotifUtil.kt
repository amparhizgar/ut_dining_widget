package com.amirhparhizgar.utdiningwidget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.amirhparhizgar.utdiningwidget.domain.CHANNEL_AUTO_REFRESH
import com.amirhparhizgar.utdiningwidget.ui.MainActivity

/**
 * Created by AmirHossein Parhizgar on 4/20/2023.
 */

fun checkAndRequestNotificationPermission(
    context: Context,
    launcher: ManagedActivityResultLauncher<String, Boolean>
) {
    if (haveNotificationPermission(context))
        return
    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
}

@ChecksSdkIntAtLeast(Build.VERSION_CODES.TIRAMISU)
fun haveNotificationPermission(
    context: Context,
): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else return true
    val permissionCheckResult = ContextCompat.checkSelfPermission(context, permission)
    return permissionCheckResult == PackageManager.PERMISSION_GRANTED
}


fun Context.notifyAutoRefreshing(success: Boolean) {
    createNotificationChannel(this)
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val builder = NotificationCompat.Builder(this, CHANNEL_AUTO_REFRESH)
        .setSmallIcon(R.mipmap.ic_apple_big)
        .setContentTitle(applicationContext.getString(if (success) R.string.auto_refresh_success_title else R.string.auto_refresh_fail_title))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
    with(NotificationManagerCompat.from(this)) {
        notify(1, builder.build())
    }
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = context.getString(R.string.auto_refresh_channel_name)
        val descriptionText = context.getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_AUTO_REFRESH, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}