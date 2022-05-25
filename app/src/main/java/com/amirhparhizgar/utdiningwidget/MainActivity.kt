package com.amirhparhizgar.utdiningwidget

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.amirhparhizgar.utdiningwidget.data.getDBInstance
import com.amirhparhizgar.utdiningwidget.ui.theme.UTDiningWidgetTheme
import com.amirhparhizgar.utdiningwidget.worker.ScrapWorker
import com.amirhparhizgar.utdiningwidget.worker.ScrapWorkerImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

val TAG = "amir"

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val USERNAME_KEY = stringPreferencesKey("username")
val PASSWORD_KEY = stringPreferencesKey("password")

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
                    val showDialog = remember { mutableStateOf(false) }
                    if (showDialog.value)
                        AccountDialog(showDialog)

                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
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
                                CircularProgressIndicator()
                            val getDataEnabled = applicationContext.dataStore.data.map {
                                it[USERNAME_KEY].isNullOrEmpty()
                                    .or(it[PASSWORD_KEY].isNullOrEmpty()).not()
                                    .and(loadingState.value.not())
                            }.collectAsState(initial = false)
                            Button(onClick = {
                                lifecycleScope.launch {
                                    loadingState.value = true
                                    ScrapWorkerImpl(applicationContext).doWork()
                                    loadingState.value = false
                                }
                            }, enabled = getDataEnabled.value) {
                                Text(text = stringResource(id = R.string.get_data_now))
                            }
                        }
                        state.value.sortedBy { it.date }.forEach {
                            FoodItem(it.toString())
                        }
                        if (state.value.isEmpty())
                            FoodItem("nothing found")
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
                    mutableStateOf<String>(runBlocking {
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

    fun saveUsernameAndPassword(username: String, password: String) =
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
fun FoodItem(name: String) {
    Text(text = name)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    UTDiningWidgetTheme {
        FoodItem("Android")
    }
}