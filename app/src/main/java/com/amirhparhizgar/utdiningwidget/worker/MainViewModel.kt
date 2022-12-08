package com.amirhparhizgar.utdiningwidget.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amirhparhizgar.utdiningwidget.ReserveRecord
import com.amirhparhizgar.utdiningwidget.data.ReserveDao
import com.amirhparhizgar.utdiningwidget.sortBasedOnMeal
import com.amirhparhizgar.utdiningwidget.toJalali
import com.amirhparhizgar.utdiningwidget.toLongFormat
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import saman.zamani.persiandate.PersianDate
import javax.inject.Inject

/**
 * Created by AmirHossein Parhizgar on 12/8/2022.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val db: ReserveDao,
): ViewModel() {

    var loadedFrom: Long = PersianDate().toLongFormat()
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
}