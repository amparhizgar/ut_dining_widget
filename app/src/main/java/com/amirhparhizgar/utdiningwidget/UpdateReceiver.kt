package com.amirhparhizgar.utdiningwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.AlarmManagerCompat
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.runBlocking
import saman.zamani.persiandate.PersianDate
import java.util.*

class UpdateReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM_UPDATE_WIDGET = "ACTION_ALARM_UPDATE_WIDGET"

        fun schedule(context: Context) {
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE
            else 0
            val pendingIntent = PendingIntent.getBroadcast(
                context, 123, Intent(
                    ACTION_ALARM_UPDATE_WIDGET
                ), flag
            )
            val c = PersianDate()
            c.hour = 4
            c.minute = 0
            c.second = 0
            c.addDay(1)
            AlarmManagerCompat.setExact(
                context.getSystemService(ALARM_SERVICE) as AlarmManager, AlarmManager.RTC_WAKEUP,
                c.time, pendingIntent
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                runBlocking {
                    DiningWidget().updateAll(context)
                }
                schedule(context)
            }
            ACTION_ALARM_UPDATE_WIDGET -> {
                runBlocking {
                    DiningWidget().updateAll(context)
                }
                schedule(context)
            }
        }
    }
}