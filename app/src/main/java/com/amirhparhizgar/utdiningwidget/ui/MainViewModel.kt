package com.amirhparhizgar.utdiningwidget.ui

import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amirhparhizgar.utdiningwidget.data.ReserveDao
import com.amirhparhizgar.utdiningwidget.data.model.ReserveRecord
import com.amirhparhizgar.utdiningwidget.data.model.sortBasedOnMeal
import com.amirhparhizgar.utdiningwidget.usecase.ScrapUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import saman.zamani.persiandate.PersianDate
import javax.inject.Inject

/**
 * Created by AmirHossein Parhizgar on 12/9/2022.
 */


/**
 * Created by AmirHossein Parhizgar on 12/8/2022.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val db: ReserveDao,
    private val scrapUseCase: ScrapUseCase
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

    val loadingState =
        mutableStateOf(false)

    val webView: View
        get() = scrapUseCase.scrapper.view

    fun getData() {
        viewModelScope.launch {
            loadingState.value = true
            withTimeout(2 * 60000) {
                scrapUseCase()
            }
            loadingState.value = false
            showWebView.value = false
        }
    }
}