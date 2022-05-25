package com.amirhparhizgar.utdiningwidget.worker

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import com.amirhparhizgar.utdiningwidget.DiningScrapper
import com.amirhparhizgar.utdiningwidget.ReserveRecord
import com.amirhparhizgar.utdiningwidget.TAG
import com.amirhparhizgar.utdiningwidget.data.getDBInstance
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*

class ScrapWorker(appContext: Context?, workerParams: WorkerParameters?) :
    ListenableWorker(appContext!!, workerParams!!) {
    private val db = getDBInstance(applicationContext).dao()
    var success = false
    private fun saveResults(list: List<ReserveRecord>) {
        runBlocking(Dispatchers.IO) {
            db.insertAll(*list.toTypedArray())
        }
    }

    @SuppressLint("RestrictedApi")
    private var mFuture: SettableFuture<Result> = SettableFuture.create()

    @SuppressLint("RestrictedApi")
    override fun startWork(): ListenableFuture<Result> {
        runBlocking(Dispatchers.Main) {
            try {
                val scrapper = DiningScrapper(applicationContext)
                val onFinished2 = { list: List<ReserveRecord> ->
                    saveResults(list)
                    Log.d(TAG, "ScrapWorker->startWork: finished next week")
                    mFuture.set(Result.success())
                    success = true
                }
                val onFinished1 = { list: List<ReserveRecord> ->
                    Log.d(TAG, "ScrapWorker->startWork: starting next week")
                    saveResults(list)
                    scrapper.onFinish = onFinished2
                    scrapper.nextWeek = true
                    scrapper.start()
                }
                scrapper.onFinish = onFinished1
                scrapper.start()

            } catch (e: Exception) {
                Log.e(TAG, "startWork: failed", e)
                mFuture.set(Result.failure())
            }
        }
        CoroutineScope(Dispatchers.Default).launch {
            delay(1 * 60 * 1000)
            if (success)
                mFuture.set(Result.success())
            else
                mFuture.set(Result.retry())
        }

        return mFuture
    }
}
