package com.amirhparhizgar.utdiningwidget

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import androidx.work.Constraints
import com.amirhparhizgar.utdiningwidget.data.getDBInstance
import com.amirhparhizgar.utdiningwidget.data.scheduleForNearestWeekendIfNotScheduled
import com.amirhparhizgar.utdiningwidget.pulltoload.PullToLoadIndicator
import com.amirhparhizgar.utdiningwidget.ui.theme.UTDiningWidgetTheme
import com.amirhparhizgar.utdiningwidget.worker.MainViewModel
import com.amirhparhizgar.utdiningwidget.worker.ScrapWorker
import com.amirhparhizgar.utdiningwidget.worker.ScrapWorkerImpl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat
import java.util.concurrent.TimeUnit

const val TAG = "amir"

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val USERNAME_KEY = stringPreferencesKey("username")
val PASSWORD_KEY = stringPreferencesKey("password")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity->onCreate: UT Dining Widget is up")
        lifecycleScope.launch(Dispatchers.Default) {
//            enqueueScrapWork()
            scheduleForNearestWeekendIfNotScheduled()

            UpdateReceiver.schedule(applicationContext)
            DiningWidget().updateAll(applicationContext)
        }


        val haveCredentials = applicationContext.dataStore.data.map {
            it[USERNAME_KEY].isNullOrEmpty()
                .or(it[PASSWORD_KEY].isNullOrEmpty()).not()
        }
        val scrapWorker = ScrapWorkerImpl(applicationContext)

        setContent {
            UTDiningWidgetTheme {

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val recordListState = viewModel.incrementalFlow.collectAsState()
                    val showDialog = remember { mutableStateOf(false) }
                    if (showDialog.value)
                        AccountDialog(showDialog)

                    Column(
                        Modifier
                            .padding(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(id = R.string.account))
                            val username = usernameFlow.collectAsState(initial = "")
                            Text(text = username.value.ifEmpty { stringResource(id = R.string.no_account) })
                            Spacer(modifier = Modifier.weight(1f))
                            Button(onClick = { showDialog.value = true }) {
                                Text(text = stringResource(id = R.string.set_account))
                            }
                        }
                        val showWebView = remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val loadingState = remember {
                                mutableStateOf(false)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (loadingState.value)
                                CircularProgressIndicator(Modifier.clickable {
                                    showWebView.value = showWebView.value.not()
                                })
                            val haveCredentialsState =
                                haveCredentials.collectAsState(initial = false)
                            val getDataEnabled =
                                haveCredentialsState.value.and(loadingState.value.not())
                            val context = LocalContext.current
                            Button(onClick = {
                                lifecycleScope.launch {
                                    loadingState.value = true
                                    kotlin.runCatching {
                                        withTimeout(2 * 60000) {
                                            scrapWorker.doWork()
                                        }
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            R.string.get_data_failed,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    loadingState.value = false
                                    showWebView.value = false
                                }
                            }, enabled = getDataEnabled) {
                                Text(text = stringResource(id = R.string.get_data_now))
                            }
                        }
                        if (showWebView.value)
                            AndroidView(modifier = Modifier.height(600.dp), factory = {
                                scrapWorker.scrapper.view
                            })
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            val loading = remember { mutableStateOf(false) }
                            val scope = rememberCoroutineScope()
                            val pullState =
                                rememberPullToLoadState(loading = loading.value, onLoad = {
                                    scope.launch {
                                        loading.value = true
                                        viewModel.loadMore()
                                        loading.value = false
                                    }
                                })

                            val lazyListState = rememberLazyListState()
                            LaunchedEffect(key1 = true) {
                                scope.launch {
                                    lazyListState.scrollToItem(
                                        viewModel.incrementalFlow.drop(1).first().size
                                    )
                                }
                            }

                            LazyColumn(
                                Modifier.pullToLoad(pullState),
                                reverseLayout = true,
                                state = lazyListState
                            ) {

                                val list = recordListState.value.groupBy { it.date }.toList()
                                    .sortedBy { it.first }.asReversed()
                                items(list.size, key = {
                                    list[it].first
                                }) { index ->
                                    val showNotReserved = remember { mutableStateOf(false) }
                                    val meals =
                                        list[index].second.distinctBy { it.meal }.map { it.meal }
                                    val reserves = list[index].second.filter { it.reserved }
                                    val notReserves =
                                        list[index].second.filter { it.reserved.not() }
                                            .distinctBy { it.meal }
                                            .filter {
                                                reserves.map { r -> r.name }.contains(it.name).not()
                                            }

                                    Day(
                                        date = list[index].first.toJalali(),
                                        showNotReserved.value,
                                        { showNotReserved.value = it },
                                        notReserves.isNotEmpty()
                                    ) {
                                        meals.forEach { meal ->
                                            FoodItem(
                                                reserves.filter { it.meal == meal },
                                                notReserves.filter { it.meal == meal },
                                                showNotReserved.value
                                            )
                                        }
                                    }
                                }
                                item {
                                    PullToLoadIndicator(loading = loading.value, state = pullState)
                                }
                            }
                            if (recordListState.value.isEmpty())
                                Text("nothing found")
                        }
                    }
                }

            }
        }
    }

    @Composable
    private fun AccountDialog(showDialog: MutableState<Boolean>) {
        Dialog(onDismissRequest = { showDialog.value = false }) {
            Card(
                backgroundColor = MaterialTheme.colors.background
            ) {
                val usernameState = remember {
                    mutableStateOf(runBlocking {
                        withTimeoutOrNull(100) { usernameFlow.firstOrNull() } ?: ""
                    })
                }
                val passwordState = remember { mutableStateOf("") }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    OutlinedTextField(
                        value = usernameState.value,
                        onValueChange = { usernameState.value = it },
                        label = { Text(stringResource(id = R.string.username)) }
                    )
                    OutlinedTextField(
                        value = passwordState.value,
                        onValueChange = { passwordState.value = it },
                        label = { Text(stringResource(id = R.string.password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Button(
                        modifier = Modifier.align(alignment = Alignment.End),
                        onClick = {
                            saveUsernameAndPassword(
                                usernameState.value,
                                passwordState.value
                            )
                            showDialog.value = false
                        }) {
                        Text(text = stringResource(id = R.string.save))
                    }
                }
            }
        }
    }

    private fun saveUsernameAndPassword(username: String, password: String) =
        lifecycleScope.launch(Dispatchers.IO) {
            applicationContext.dataStore.edit {
                it[USERNAME_KEY] = username
                it[PASSWORD_KEY] = password
            }
        }

    private val usernameFlow: Flow<String> by lazy {
        applicationContext.dataStore.data
            .map { preferences ->
                preferences[USERNAME_KEY] ?: ""
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
fun Day(
    date: PersianDate,
    showNotReserved: Boolean,
    onSetShowNotReserved: (Boolean) -> Unit,
    showButton: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        Modifier
            .padding(4.dp)
            .fillMaxWidth(1f), elevation = 2.dp, shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row {
                Text(text = date.dayName(), color = MaterialTheme.colors.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = PersianDateFormat("j F").format(date))
                if (showButton) {
                    val rotationState =
                        animateFloatAsState(targetValue = if (showNotReserved) 180f else 0f)
                    Icon(
                        modifier = Modifier
                            .padding(0.dp)
                            .clickable {
                                onSetShowNotReserved(showNotReserved.not())
                            }
                            .rotate(rotationState.value),
                        painter = painterResource(id = R.drawable.ic_baseline_expand_more_24),
                        contentDescription = "show not reserved"
                    )
                }
            }
            Divider(Modifier.padding(vertical = 4.dp))
            this.content()
        }
    }

}

@Composable
fun FoodItem(
    reserves: List<ReserveRecord>,
    notReserves: List<ReserveRecord>,
    showNotReserved: Boolean
) {
    Column {
        Text(
            text = reserves.getOrElse(0) { notReserves[0] }.meal,
            color = MaterialTheme.colors.primary
        )
        reserves.forEach { reserved ->
            Text(text = "✅ ${reserved.name}")
            Text(
                text = reserved.restaurant, fontSize = 10.sp
            )
        }
        AnimatedVisibility(visible = showNotReserved) {
            notReserves.forEach {
                Text(text = "❌ ${it.name}", color = LocalContentColor.current.copy(alpha = 0.4f))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    UTDiningWidgetTheme {

    }
}

fun String.toJalali(): PersianDate {
    //1401/11/30
    //0123456789
    return PersianDate().initJalaliDate(
        substring(0, 4).toInt(),
        substring(5, 7).toInt(),
        substring(8, 10).toInt()
    )
}

fun Long.toJalali(): PersianDate {
    //14011130
    //01234567
    toString().apply {
        return PersianDate().initJalaliDate(
            substring(0, 4).toInt(),
            substring(4, 6).toInt(),
            substring(6, 8).toInt()
        )
    }
}

fun PersianDate.toLongFormat(): Long {
    return shYear * 10000L + shMonth * 100 + shDay
}