package com.amirhparhizgar.utdiningwidget.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.amirhparhizgar.utdiningwidget.data.model.ReserveRecord
import com.amirhparhizgar.utdiningwidget.pulltoload.PullToLoadIndicator
import com.amirhparhizgar.utdiningwidget.pulltoload.pullToLoad
import com.amirhparhizgar.utdiningwidget.pulltoload.rememberPullToLoadState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Created by AmirHossein Parhizgar on 12/10/2022.
 */


@Composable
fun DataSection(recordListState: State<List<ReserveRecord>>, viewModel: MainViewModel) {
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
