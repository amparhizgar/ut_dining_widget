package com.amirhparhizgar.utdiningwidget.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.amirhparhizgar.utdiningwidget.DiningScrapper
import com.amirhparhizgar.utdiningwidget.ReserveRecord
import com.amirhparhizgar.utdiningwidget.TAG
import com.amirhparhizgar.utdiningwidget.data.getDBInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ScrapWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return ScrapWorkerImpl(applicationContext).doWork()
    }

}

class ScrapWorkerImpl(applicationContext: Context) {

    val scrapper = DiningScrapper(applicationContext)

    suspend fun doWork(): ListenableWorker.Result {
        kotlin.runCatching {
            val list1 = withTimeout(60 * 1000) {
                scrapper.start()
            }
            saveResults(list1)
            val list2 = withTimeout(60 * 1000) {
                scrapper.nextWeek = true
                scrapper.start()
            }
            saveResults(list2)
        }.onFailure {
            Log.e(TAG, "doWork: Error Occurred", it)
            return ListenableWorker.Result.retry()
        }
        return ListenableWorker.Result.success()
    }

    private val db = getDBInstance(applicationContext).dao()

    private suspend fun saveResults(list: List<ReserveRecord>) {
        withContext(Dispatchers.IO) {
            db.insertAll(*list.toTypedArray())
        }
    }
}