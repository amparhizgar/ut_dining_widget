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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ScrapWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return ScrapWorkerImpl(applicationContext).doWork()
    }

}

class ScrapWorkerImpl(private val applicationContext: Context) {

    val scrapper = DiningScrapper(applicationContext)

    suspend fun doWork(): ListenableWorker.Result {
        kotlin.runCatching {
            val list1 = withTimeout(60 * 1000) {
                suspendCoroutine<List<ReserveRecord>> { cont ->
                    val onFinished = { list: List<ReserveRecord> ->
                        Log.d(TAG, "ScrapWorker->startWork: current week done")
                        cont.resume(list)
                    }
                    scrapper.onFinish = onFinished
                    scrapper.start()
                }
            }
            saveResults(list1)
            val list2 = withTimeout(60 * 1000) {
                suspendCoroutine<List<ReserveRecord>> { cont ->
                    val onFinished = { list: List<ReserveRecord> ->
                        Log.d(TAG, "ScrapWorker->startWork: next week done")
                        cont.resume(list)
                    }
                    scrapper.onFinish = onFinished
                    scrapper.nextWeek = true
                    scrapper.start()
                }
            }
            saveResults(list2)
        }.onFailure {
            return ListenableWorker.Result.retry()
        }
        return ListenableWorker.Result.success()
    }

    private val db = getDBInstance(applicationContext).dao()

    private fun saveResults(list: List<ReserveRecord>) {
        runBlocking(Dispatchers.IO) {
            db.insertAll(*list.toTypedArray())
        }
    }
}