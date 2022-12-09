package com.amirhparhizgar.utdiningwidget.data

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.icu.util.Calendar
import androidx.activity.ComponentActivity
import com.amirhparhizgar.utdiningwidget.domain.ScrapJobService

/**
 * Created by AmirHossein Parhizgar on 12/8/2022.
 */

fun Context.scheduleForNearestWeekendIfNotScheduled() {
    val jobScheduler = getSystemService(ComponentActivity.JOB_SCHEDULER_SERVICE) as JobScheduler
    if (jobScheduler.getPendingJob(123) != null)
        return
    val jobInfo = JobInfo.Builder(
        123,
        ComponentName(applicationContext, ScrapJobService::class.java)
    )
    val startTime = getStartTime(Calendar.getInstance())
    val startDelay = startTime - System.currentTimeMillis()
    val job = jobInfo.setRequiresCharging(true)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
        .setMinimumLatency(startDelay)
        .setOverrideDeadline(startDelay + 2 * 24 * 3600 * 1000L)
        .build()
    jobScheduler.schedule(job)
}

private fun getStartTime(calendar: Calendar): Long {
    // sunday  = 1 Wednesday = 4, Friday = 6
    val dayOfWeek = calendar[Calendar.DAY_OF_WEEK]
    val plusDays = (Calendar.THURSDAY - dayOfWeek + 7) % 7
    val start = Calendar.getInstance().apply { timeInMillis = calendar.timeInMillis }
    start.add(Calendar.DAY_OF_MONTH, plusDays)
    start.set(Calendar.HOUR_OF_DAY, 0)
    start.set(Calendar.MINUTE, 0)
    start.set(Calendar.SECOND, 0)
    start.set(Calendar.MILLISECOND, 0)
    if (start.timeInMillis > calendar.timeInMillis)
        return start.timeInMillis
    start.add(Calendar.DAY_OF_MONTH, 7)
    return start.timeInMillis
}