package com.amirhparhizgar.utdiningwidget

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.daandtu.webscraper.WebScraper
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.lang.Exception

const val RESTAURANT = "Restaurant"
const val PERSON_GROUP_ID = "PersonGroup"
val DELAY: Long = 3000

@SuppressLint("SetJavaScriptEnabled")
class DiningScrapper(
    context: Context,
    private var onFinish: ((List<ReserveRecord>) -> Unit)
) : WebScraper(context) {
    val scope = CoroutineScope(Dispatchers.Default)

    init {
        setUserAgentToDesktop(true) //default: false
        setLoadImages(true) //default: false
        webView.settings.javaScriptEnabled = true
    }

    private val theList = mutableListOf<ReserveRecord>()
    fun start() {
        clearAll()
        loadURL("https://dining2.ut.ac.ir")
        setOnPageLoadedListener {
            val usernameField = findElementById("username")
            val passwordField = findElementById("password")
            val submit = findElementByName("submit")
            usernameField.text = "*****************"
            passwordField.text = "*****************"
            setOnPageLoadedListener(::loadReserve)
            submit.click()
        }
    }

    private fun loadReserve(str: String) {
        setOnPageLoadedListener(::onReservePageLoaded)
        loadURL("https://dining2.ut.ac.ir/Reserves")
    }

    private var groupIndexToRun = 0
    private var restaurantIndexToRun = 0
    private var groups = emptyList<String>()
    private var restaurants = emptyList<String>()

    private fun onReservePageLoaded(str: String) {
        val personGroup = Jsoup.parse(html).getElementById(PERSON_GROUP_ID)
        groups = personGroup?.children()?.map {
            it.attr("value")
        }?.filter { it != "0" } ?: return

        nextGroup()
    }

    private fun nextGroup() {
        val group = groups[groupIndexToRun]
        groupIndexToRun += 1
        Log.d(TAG, "DiningScrapper->nextGroup: selecting group $group")
        webView.evaluateJavascript(
            "element692 = " +
                    "document.getElementById(\"$PERSON_GROUP_ID\");" +
                    " element692.value = \"$group\";", this::onGroupSelected
        )
    }

    private fun onGroupSelected(it: String) = scope.launch {
        delay(DELAY)
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "getRest(false);"
            ) {
                scope.launch {
                    delay(DELAY)
                    withContext(Dispatchers.Main) {
                        val restaurantSelect = Jsoup.parse(html).getElementById(RESTAURANT)
                        restaurants = restaurantSelect?.children()?.map {
                            it.attr("value")
                        }?.filter { it != "0" } ?: throw Exception("empty restaurant!")

                        restaurantIndexToRun = 0
                        nextRestaurant()
                    }
                }
            }
        }
    }

    private fun nextRestaurant() {
        val restaurant = restaurants[restaurantIndexToRun]
        restaurantIndexToRun += 1
        Log.d(TAG, "DiningScrapper->nextRestaurant: selecting restaurant: $restaurant")
        setRestaurant(restaurant) { // on reservation fetch requested:
            scope.launch {
                delay(DELAY)
                withContext(Dispatchers.Main) {
                    // extract table
                    val masterDivXpath = "//*[@id=\"myTabContent6\"]/div[2]"
                    val parsed = Jsoup.parse(html)
                    val masterDiv = parsed.selectXpath(masterDivXpath)[0]
                    masterDiv.children().drop(2).forEach {
                        val header = it.children()[0]
                        val mealName = header.child(0).text()
                        it.children().drop(1).forEach { day ->
                            val date = day.getElementById("DateDiv")?.text()
                            val foods = day.getElementsByClass("reserveFoodFoodDiv")
                            foods.forEach { foodItem ->
                                val checked = foodItem.getElementsByTag("input")[0]
                                    .attr("checked") == "checked"
                                val foodNameLabels = foodItem.getElementsByTag("label")
                                val label = foodNameLabels[0].ownText()
                                if (checked) {
                                    theList.add(ReserveRecord(date!!, mealName, label))
                                    Log.d(TAG, "$date: $mealName: $label")
                                }
                            }
                        }
                    }
                    if (restaurantIndexToRun < restaurants.size)
                        nextRestaurant()
                    else
                        if (groupIndexToRun < groups.size)
                            nextGroup()
                        else
                            onFinish(theList)
                }
            }
        }
    }

    private fun setRestaurant(restaurant: String, onReservationFetched: (String) -> Unit) {
        webView.evaluateJavascript(
            "element492 = " +
                    "document.getElementById(\"$RESTAURANT\");" +
                    " element492.value = \"${restaurant}\";"
        ) {
            webView.evaluateJavascript(
                "getReservePage();", onReservationFetched
            )
        }
    }

    private val webView
        get() = view as WebView
}