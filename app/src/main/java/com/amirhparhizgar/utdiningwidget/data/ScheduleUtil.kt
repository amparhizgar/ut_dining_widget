package com.amirhparhizgar.utdiningwidget.data

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.icu.util.Calendar
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import com.amirhparhizgar.utdiningwidget.BuildConfig
import com.amirhparhizgar.utdiningwidget.domain.ScrapJobService
import com.amirhparhizgar.utdiningwidget.ui.TAG
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by AmirHossein Parhizgar on 12/8/2022.
 */

fun Context.scheduleForNearestWeekendIfNotScheduled(isEnabled: Boolean) {
    if (isEnabled.not()) return
    val jobScheduler = getSystemService(ComponentActivity.JOB_SCHEDULER_SERVICE) as JobScheduler
    if (jobScheduler.getPendingJob(123) != null)
        return
    scheduleForNearestWeekend()
}

private fun Context.scheduleForNearestWeekend() {
    val jobScheduler = getSystemService(ComponentActivity.JOB_SCHEDULER_SERVICE) as JobScheduler
    val startDelay =
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val epochMillisDiff = Calendar.getInstance().apply {
                set(Calendar.YEAR, 1970)
                set(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val fiveMinutesInMillis = TimeUnit.HOURS.toMillis(4)
            val secondsPastLastRoundMinute = (System.currentTimeMillis() - epochMillisDiff) % fiveMinutesInMillis
            fiveMinutesInMillis - secondsPastLastRoundMinute
        } else
            getNextWeekendMillis(Calendar.getInstance()) - System.currentTimeMillis()

    val deadLineDelay =
        if (BuildConfig.DEBUG)
            startDelay + TimeUnit.MINUTES.toMillis(2)
        else
            startDelay + TimeUnit.DAYS.toMillis(1)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        Log.d(TAG, "scheduleForNearestWeekend: ${Date(startDelay + System.currentTimeMillis())}}")
    val jobInfo =
        JobInfo.Builder(123, ComponentName(applicationContext, ScrapJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(startDelay)
            .setOverrideDeadline(deadLineDelay)
            .setPersisted(true)
            .build()
    jobScheduler.schedule(jobInfo)
}

private fun getNextWeekendMillis(calendar: Calendar): Long {
    // sunday  = 1 Wednesday = 4, Friday = 6
    val dayOfWeek = calendar[Calendar.DAY_OF_WEEK]
    val plusDays = (Calendar.THURSDAY - dayOfWeek + 7) % 7
    val weekend = Calendar.getInstance().apply { timeInMillis = calendar.timeInMillis }
    weekend.add(Calendar.DAY_OF_MONTH, plusDays)
    weekend.set(Calendar.HOUR_OF_DAY, 0)
    weekend.set(Calendar.MINUTE, 0)
    weekend.set(Calendar.SECOND, 0)
    weekend.set(Calendar.MILLISECOND, 0)
    if (weekend.timeInMillis > weekend.timeInMillis)
        return weekend.timeInMillis
    weekend.add(Calendar.DAY_OF_MONTH, 7)
    return weekend.timeInMillis
}

fun Context.cancelRefreshingJob() {
    val jobScheduler = getSystemService(ComponentActivity.JOB_SCHEDULER_SERVICE) as JobScheduler
    jobScheduler.cancel(123)
    Log.i(TAG, "cancelRefreshingJob: Job unscheduled")
}