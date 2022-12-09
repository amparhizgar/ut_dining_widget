package com.amirhparhizgar.utdiningwidget.usecase

import android.util.Log
import androidx.work.ListenableWorker
import com.amirhparhizgar.utdiningwidget.domain.DiningScrapper
import com.amirhparhizgar.utdiningwidget.data.ReserveDao
import com.amirhparhizgar.utdiningwidget.data.model.ReserveRecord
import com.amirhparhizgar.utdiningwidget.ui.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class ScrapUseCase @Inject constructor(
    val scrapper: DiningScrapper,
    private val db: ReserveDao,
) {
    suspend operator fun invoke(): ListenableWorker.Result {
        kotlin.runCatching {
            withContext(Dispatchers.IO) {
                val list1 = withTimeout(60 * 1000) {
                    scrapper.start()
                }
                saveResults(list1)
                val list2 = withTimeout(60 * 1000) {
                    scrapper.nextWeek = true
                    scrapper.start()
                }
                saveResults(list2)
            }
        }.onFailure {
            Log.e(TAG, "doWork: Error Occurred", it)
            return ListenableWorker.Result.retry()
        }
        return ListenableWorker.Result.success()
    }

    private suspend fun saveResults(list: List<ReserveRecord>) {
        withContext(Dispatchers.IO) {
            db.insertAll(*list.toTypedArray())
        }
    }
}