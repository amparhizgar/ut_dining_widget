package com.amirhparhizgar.utdiningwidget.domain

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import androidx.work.Configuration
import com.amirhparhizgar.utdiningwidget.data.getDBInstance
import com.amirhparhizgar.utdiningwidget.data.model.ReserveRecord
import com.amirhparhizgar.utdiningwidget.data.scheduleForNearestWeekendIfNotScheduled
import com.amirhparhizgar.utdiningwidget.ui.TAG
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class ScrapJobService : JobService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    @Inject
    lateinit var scrapper: DiningScrapper

    init {
        val builder: Configuration.Builder = Configuration.Builder()
        builder.setJobSchedulerJobIdRange(0, 1000)
    }

    override fun onStartJob(jobParameters: JobParameters?): Boolean {
        scope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    val list1 = withTimeout(10 * 1000) {
                        scrapper.start()
                    }
                    saveResults(list1)
                    val list2 = withTimeout(10 * 1000) {
                        scrapper.nextWeek = true
                        scrapper.start()
                    }
                    saveResults(list2)
                }
                jobFinished(jobParameters, false)
                scheduleForNearestWeekendIfNotScheduled()
            }.onFailure {
                Log.e(TAG, "doWork: Error Occurred", it)
                jobFinished(jobParameters, true)
            }
        }
        return true // work is in background
    }

    override fun onStopJob(p0: JobParameters?): Boolean {
        scope.cancel()
        return true // we want the job to be restarted
    }

    private suspend fun saveResults(list: List<ReserveRecord>) {
        withContext(Dispatchers.IO) {
            val db = getDBInstance(applicationContext).dao()
            db.insertAll(*list.toTypedArray())
        }
    }
}