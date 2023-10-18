package com.amirhparhizgar.utdiningwidget.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.updateAll
import com.amirhparhizgar.utdiningwidget.data.scheduleForNearestWeekendIfNotScheduled
import com.amirhparhizgar.utdiningwidget.ui.DiningWidget
import com.amirhparhizgar.utdiningwidget.ui.TAG
import com.amirhparhizgar.utdiningwidget.ui.autoRefreshEnabled
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import saman.zamani.persiandate.PersianDate
import javax.inject.Inject

@AndroidEntryPoint
class UpdateWidgetReceiver : BroadcastReceiver() {

    @Inject
    lateinit var datastore: DataStore<Preferences>

    companion object {
        const val ACTION_ALARM_UPDATE_WIDGET = "ACTION_ALARM_UPDATE_WIDGET"

        fun schedule(context: Context) {
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE
            else 0
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                123,
                Intent(context, UpdateWidgetReceiver::class.java).apply {
                    action = ACTION_ALARM_UPDATE_WIDGET
                },
                flag
            )
            val c = PersianDate()
            c.hour = 23 // hour at witch widget get updated
            c.minute = 0
            c.second = 0
            if (c.time <= System.currentTimeMillis())
                c.addDay(1)
            (context.getSystemService(ALARM_SERVICE) as AlarmManager).set(
                AlarmManager.RTC_WAKEUP,
                c.time, pendingIntent
            )
            Log.d(TAG, "UpdateReceiver->schedule: widget update set for $c")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "UpdateReceiver->onReceive: intent: $intent")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                runBlocking {
                    DiningWidget().updateAll(context)
                }
                schedule(context)
                runBlocking {
                    context.scheduleForNearestWeekendIfNotScheduled(datastore.data.first().autoRefreshEnabled)
                }
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