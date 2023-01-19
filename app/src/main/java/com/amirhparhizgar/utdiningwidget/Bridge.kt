package com.amirhparhizgar.utdiningwidget

import android.webkit.JavascriptInterface
import androidx.annotation.Keep

/**
 * Created by AmirHossein Parhizgar on 1/11/2023.
 */
typealias ResultListener = (String) -> Unit

class Bridge {
    private val setRestaurantsListeners: MutableMap<Int, ResultListener> = mutableMapOf()
    private val setReservesListeners: MutableMap<Pair<Int, Int>, ResultListener> = mutableMapOf()

    fun addSetRestaurantsListener(groupId: Int, listener: ResultListener) {
        setRestaurantsListeners[groupId] = listener
    }
    fun addSetReservesListener(groupId: Int, restaurantId: Int, listener: ResultListener) {
        setReservesListeners[Pair(groupId, restaurantId)] = listener
    }

    @Suppress("unused")
    @JavascriptInterface
    @Keep
    fun setRestaurantsForGroup(groupId: Int, result: String) {
        setRestaurantsListeners[groupId]?.let { it(result) }
    }

    @Suppress("unused")
    @JavascriptInterface
    @Keep
    fun setReserves(groupId: Int, restaurantId: Int, result: String) {
        setReservesListeners[Pair(groupId, restaurantId)]?.let { it(result) }
    }
}