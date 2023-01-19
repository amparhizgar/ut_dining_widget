package com.amirhparhizgar.utdiningwidget.domain

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import androidx.work.Configuration
import com.amirhparhizgar.utdiningwidget.data.scheduleForNearestWeekend
import com.amirhparhizgar.utdiningwidget.ui.TAG
import com.amirhparhizgar.utdiningwidget.usecase.ScrapUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScrapJobService : JobService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    @Inject
    lateinit var scrapper: DiningScrapper

    @Inject
    lateinit var scrapUseCase: ScrapUseCase

    init {
        val builder: Configuration.Builder = Configuration.Builder()
        builder.setJobSchedulerJobIdRange(0, 1000)
    }

    override fun onStartJob(jobParameters: JobParameters?): Boolean {
        scope.launch {
            Log.i(TAG, "onStartJob")
            val result = scrapUseCase.invoke()
            if (result.isSuccess) {
                Log.i(TAG, "ScrapJobService-> Job Done :P")
                jobFinished(jobParameters, false)
                scheduleForNearestWeekend()
            } else {
                Log.e(TAG, "doWork: Error Occurred. Retrying later...", result.exceptionOrNull())
                jobFinished(jobParameters, true)
            }
        }
        return true // work is in background
    }

    override fun onStopJob(p0: JobParameters?): Boolean {
        scope.cancel()
        Log.i(TAG, "onStopJob")
        return true // we want the job to be restarted
    }
}