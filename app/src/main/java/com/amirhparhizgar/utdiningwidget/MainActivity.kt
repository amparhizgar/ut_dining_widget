package com.amirhparhizgar.utdiningwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.work.*
import com.amirhparhizgar.utdiningwidget.data.getDBInstance
import com.amirhparhizgar.utdiningwidget.ui.theme.UTDiningWidgetTheme
import com.amirhparhizgar.utdiningwidget.worker.ScrapWorker
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.TimeUnit

val TAG = "amir"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enqueueScrapWork()
        val db = getDBInstance(this).dao()
        val f = db.loadAll()

        setContent {
            UTDiningWidgetTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val state = f.collectAsState(initial = emptyList())
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        state.value.sortedBy { it.date }.forEach {
                            Greeting(it.toString())
                        }
                        if (state.value.isEmpty())
                            Greeting("nothing found")
                    }
                }
            }
        }
    }

    private fun enqueueScrapWork() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest =
            PeriodicWorkRequestBuilder<ScrapWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            "scrap",
            ExistingPeriodicWorkPolicy.KEEP, workRequest
        )
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = name)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    UTDiningWidgetTheme {
        Greeting("Android")
    }
}