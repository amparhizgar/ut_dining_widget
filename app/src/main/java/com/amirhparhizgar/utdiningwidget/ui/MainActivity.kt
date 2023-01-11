package com.amirhparhizgar.utdiningwidget.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.amirhparhizgar.utdiningwidget.BuildConfig
import com.amirhparhizgar.utdiningwidget.R
import com.amirhparhizgar.utdiningwidget.data.model.ReserveRecord
import com.amirhparhizgar.utdiningwidget.data.scheduleForNearestWeekendIfNotScheduled
import com.amirhparhizgar.utdiningwidget.domain.UpdateWidgetReceiver
import com.amirhparhizgar.utdiningwidget.ui.theme.UTDiningWidgetTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat

const val TAG = "amir"

val USERNAME_KEY = stringPreferencesKey("username")
val PASSWORD_KEY = stringPreferencesKey("password")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity->onCreate: UT Dining Widget is up")
        lifecycleScope.launch(Dispatchers.Default) {
            scheduleForNearestWeekendIfNotScheduled()

            UpdateWidgetReceiver.schedule(applicationContext)
            DiningWidget().updateAll(applicationContext)
        }

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
                        AccountDialog(
                            onDismissRequest = { showDialog.value = false },
                            username = runBlocking {
                                viewModel.usernameFlow.firstOrNull() ?: ""
                            },
                            onSave = { username, password ->
                                viewModel.saveUsernameAndPassword(username, password)
                            })

                    var showMenu by remember { mutableStateOf(false) }

                    val haveCredentialsState =
                        viewModel.haveCredentials.collectAsState(initial = false)
                    val scaffoldState = rememberScaffoldState()

                    LaunchedEffect(Unit) {
                        viewModel.showMessageEvent.collectLatest { event ->
                            val message = when (event) {
                                MainViewModel.MessageEvent.NoConnection -> "No Connection"
                                MainViewModel.MessageEvent.TimeOutError -> "Took too long! try again"
                                MainViewModel.MessageEvent.UnknownError -> "Unknown Error Occurred"
                            }
                            scaffoldState.snackbarHostState.showSnackbar(
                                message = message,
                                duration = SnackbarDuration.Short
                            )
                        }
                    }

                    val getDataEnabled = remember {
                        derivedStateOf {
                            haveCredentialsState.value.and(viewModel.loadingState.value.not())
                        }
                    }

                    Scaffold(scaffoldState = scaffoldState, topBar = {
                        DiningTopBar(
                            getDataEnabled.value,
                            showMenu,
                            onShowMenu = { showMenu = it },
                            onShowAccountDialog = { showDialog.value = true },
                            onGetData = { viewModel.getData() }
                        )
                    }) {
                        Column(
                            Modifier
                                .padding(it)
                        ) {

                            val username = viewModel.usernameFlow.collectAsState(initial = "")

                            TopSection(
                                username.value, viewModel.loadingState.value,
                                onShowAccountDialog = {
                                    showDialog.value = true
                                },
                                onToggleWebView = {
                                    viewModel.showWebView.value = viewModel.showWebView.value.not()
                                }
                            )

                            if (viewModel.showWebView.value)
                                AndroidView(modifier = Modifier.fillMaxHeight(), factory = {
                                    viewModel.webView
                                })

                            DataSection(recordListState, viewModel)
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun DiningTopBar(
    getDataEnabled: Boolean,
    showMenu: Boolean,
    onShowMenu: (Boolean) -> Unit,
    onShowAccountDialog: () -> Unit,
    onGetData: () -> Unit,
) {
    TopAppBar(
        title = { Text("UT Dining Widget") },
        actions = {
            IconButton(
                onClick = onGetData,
                enabled = getDataEnabled
            ) {
                Icon(Icons.Default.Refresh, "Refresh")
            }
            IconButton(onClick = { onShowMenu(!showMenu) }) {
                Icon(Icons.Default.MoreVert, null)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onShowMenu(false) }
            ) {
                DropdownMenuItem(onClick = {
                    onShowAccountDialog()
                    onShowMenu(false)
                }) {
                    Text(text = stringResource(id = R.string.set_account))
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Filled.AccountCircle, null)
                }
            }
        }
    )
}


@Composable
private fun TopSection(
    username: String,
    isLoading: Boolean,
    onShowAccountDialog: () -> Unit,
    onToggleWebView: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(id = R.string.account))
            Text(text = username.ifEmpty { stringResource(id = R.string.no_account) })
            if (username.isBlank()) {
                Button(onClick = onShowAccountDialog) {
                    Text(text = stringResource(id = R.string.set_account))
                }
            }
        }
        val uriHandler = LocalUriHandler.current
        Row(Modifier.clickable {
            uriHandler.openUri("https://github.com/amparhizgar/ut_dining_widget/")
        }) {
            Text(text = "V${BuildConfig.VERSION_NAME} Source code available on GitHub")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Outlined.Info, contentDescription = "Info")
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = isLoading) {
                CircularProgressIndicator(Modifier.clickable { onToggleWebView() })
            }
        }
    }
}

@Composable
fun AccountDialog(
    onDismissRequest: () -> Unit,
    username: String,
    onSave: (username: String, pass: String) -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            backgroundColor = MaterialTheme.colors.background
        ) {
            val usernameState = remember {
                mutableStateOf(username)
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
                        onSave(
                            usernameState.value,
                            passwordState.value
                        )
                        onDismissRequest()
                    }) {
                    Text(text = stringResource(id = R.string.save))
                }
            }
        }
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
            Row(Modifier.clickable(enabled = showButton) {
                onSetShowNotReserved(showNotReserved.not())
            }) {
                Text(text = date.dayName(), color = MaterialTheme.colors.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = PersianDateFormat("j F").format(date))
                if (showButton) {
                    val rotationState =
                        animateFloatAsState(targetValue = if (showNotReserved) 180f else 0f)
                    Icon(
                        modifier = Modifier
                            .padding(0.dp)
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
                Text(
                    text = "❌ ${it.name}",
                    color = LocalContentColor.current.copy(alpha = 0.4f)
                )
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