package com.amirhparhizgar.utdiningwidget.usecase

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.amirhparhizgar.utdiningwidget.data.ReserveDao
import com.amirhparhizgar.utdiningwidget.domain.DiningScrapper
import com.amirhparhizgar.utdiningwidget.ui.DiningWidget
import com.amirhparhizgar.utdiningwidget.ui.TAG
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class ScrapUseCase @Inject constructor(
    val scrapper: DiningScrapper,
    private val db: ReserveDao,
    @ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(): Result<Boolean> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val grabJob = launch {
                    for (reserveRecord in scrapper.results) {
                        db.insertAll(reserveRecord)
                    }
                }
                withTimeout(20 * 7000) {
                    scrapper.login()
                }
                withTimeout(90 * 1500) {
                    scrapper.nextWeek = false
                    scrapper.start()
                }
                withTimeout(90 * 1500) {
                    scrapper.nextWeek = true
                    scrapper.start()
                }
                DiningWidget().updateAll(context)
                grabJob.cancel()
                true
            }
        }.onFailure {
            Log.e(TAG, "doWork: Error Occurred", it)
        }
    }
}