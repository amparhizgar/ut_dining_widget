package com.amirhparhizgar.utdiningwidget.ui

import android.content.Context
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amirhparhizgar.utdiningwidget.data.ReserveDao
import com.amirhparhizgar.utdiningwidget.data.cancelRefreshingJob
import com.amirhparhizgar.utdiningwidget.data.model.ReserveRecord
import com.amirhparhizgar.utdiningwidget.data.model.sortBasedOnMeal
import com.amirhparhizgar.utdiningwidget.data.scheduleForNearestWeekend
import com.amirhparhizgar.utdiningwidget.domain.WebResourceException
import com.amirhparhizgar.utdiningwidget.usecase.ScrapUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import saman.zamani.persiandate.PersianDate
import javax.inject.Inject

/**
 * Created by AmirHossein Parhizgar on 12/8/2022.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val db: ReserveDao,
    private val scrapUseCase: ScrapUseCase,
) : ViewModel() {

    private var loadedFrom: Long = PersianDate().toLongFormat()

    private val initialFlow by lazy {
        db.loadAllAfter(loadedFrom).map { it.sortBasedOnMeal() }
    }

    val incrementalFlow = MutableStateFlow<List<ReserveRecord>>(emptyList())

    init {
        viewModelScope.launch {
            initialFlow.collect {
                incrementalFlow.value = it
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            val end = loadedFrom.toJalali().addDay(-1)
            val start = PersianDate(end.time).addDay(-7)
            loadedFrom = start.toLongFormat()
            withContext(Dispatchers.IO) {
                incrementalFlow.value = incrementalFlow.value + db.loadAllBetween(
                    start.toLongFormat(),
                    end.toLongFormat()
                ).sortBasedOnMeal()
            }
        }
    }

    val showWebView = mutableStateOf(false)

    val showMessageEvent = MutableSharedFlow<MessageEvent>()

    enum class MessageEvent {
        NoConnection, TimeOutError, UnknownError;
    }

    val loadingState =
        mutableStateOf(false)

    val webView: View
        get() = scrapUseCase.scrapper.view

    val usernameFlow: Flow<String> by lazy {
        dataStore.data
            .map { preferences ->
                preferences[USERNAME_KEY] ?: ""
            }
    }

    val autoRefreshFlow: Flow<Boolean> by lazy {
        dataStore.data
            .map { preferences ->
                preferences[AUTO_REFRESH_KEY] ?: false
            }
    }

    fun setAutoRefresh(enable: Boolean, context: Context) {
        viewModelScope.launch {
            dataStore.edit {
                it[AUTO_REFRESH_KEY] = enable
            }
            if (enable)
                context.scheduleForNearestWeekend()
            else
                context.cancelRefreshingJob()
        }
    }

    val haveCredentials = dataStore.data.map {
        it[USERNAME_KEY].isNullOrEmpty()
            .or(it[PASSWORD_KEY].isNullOrEmpty()).not()
    }

    fun getData() {
        viewModelScope.launch {
            loadingState.value = true
            val result = withTimeout(2 * 60_000) {
                scrapUseCase()
            }
            if (result.isFailure)
                when (result.exceptionOrNull()) {
                    is WebResourceException ->
                        showMessageEvent.emit(MessageEvent.NoConnection)
                    is TimeoutCancellationException ->
                        showMessageEvent.emit(MessageEvent.TimeOutError)
                    else ->
                        showMessageEvent.emit(MessageEvent.UnknownError)
                }
            loadingState.value = false
            showWebView.value = false
        }
    }

    fun saveUsernameAndPassword(username: String, password: String) =
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit {
                it[USERNAME_KEY] = username
                it[PASSWORD_KEY] = password
            }
        }

}