package com.amirhparhizgar.utdiningwidget

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.daandtu.webscraper.WebScraper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jsoup.Jsoup

const val RESTAURANT = "Restaurant"
const val PERSON_GROUP_ID = "PersonGroup"
const val DELAY: Long = 1000

@SuppressLint("SetJavaScriptEnabled")
class DiningScrapper(
    context: Context
) : WebScraper(context) {
    val scope = CoroutineScope(Dispatchers.Default)
    var nextWeek: Boolean = false
    var onFinish: ((List<ReserveRecord>) -> Unit)? = null
    private lateinit var username: String
    private lateinit var password: String
    private val dataStore = context.dataStore.data
    private var onPageLoadedListener: (() -> Unit)? = null

    init {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, url: String): WebResourceResponse? {
                Log.d(TAG, "shouldInterceptRequest: $url")
                return if (url.endsWith(".css")
                    || url.endsWith(".ico")
                ) { // add other specific resources..
                    WebResourceResponse(
                        "text/css",
                        "UTF-8", "".byteInputStream()
                    )
                } else
                    super.shouldInterceptRequest(view, url)

            }

            override fun onPageFinished(view: WebView, url: String) {
                onPageLoadedListener?.invoke()
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
                webView.isDrawingCacheEnabled = true
            }
        }

        setUserAgentToDesktop(true) //default: false
        setLoadImages(false) //default: false
        webView.settings.javaScriptEnabled = true
    }

    private var groupIndexToRun = 0
    private var restaurantIndexToRun = 0

    private val theList = mutableListOf<ReserveRecord>()
    fun start() {
        clearAll()
        runBlocking {
            dataStore.map {
                username = it[USERNAME_KEY] ?: ""
                password = it[PASSWORD_KEY] ?: ""
            }.first()
        }
        groupIndexToRun = 0
        restaurantIndexToRun = 0
        onPageLoadedListener = {
            val usernameField = findElementById("username")
            val passwordField = findElementById("password")
            val submit = findElementByName("submit")
            usernameField.text = username
            passwordField.text = password
            onPageLoadedListener = ::loadReserve
            submit.click()
        }
        loadURL("https://dining2.ut.ac.ir")
    }

    private fun loadReserve() {
        onPageLoadedListener = (::onReservePageLoaded)
        loadURL("https://dining2.ut.ac.ir/Reserves")
    }


    private var groups = emptyList<String>()
    private var restaurants = emptyList<String>()
    private var restaurantNames = emptyList<String>()

    private fun onReservePageLoaded() {
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
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "getRest(false);"
            ) {
                scope.launch {
                    Log.d(TAG, "delay 3")
                    delay(DELAY)
                    withContext(Dispatchers.Main) {
                        val restaurantSelect = Jsoup.parse(html).getElementById(RESTAURANT)
                        restaurants = restaurantSelect?.children()?.map {
                            it.attr("value")
                        }?.filter { it != "0" } ?: throw Exception("empty restaurant!")

                        restaurantNames = restaurantSelect.children().map {
                            it.ownText()
                        }.filter { it != "0" }

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
        if (restaurantIndexToRun > 1)
            setRestaurant(restaurant, this::extract)
        else
            extract("")
    }

    private fun extract(unused: String) {
        scope.launch {
            Log.d(TAG, "delay 2")
            delay(DELAY)
            if (nextWeek && restaurantIndexToRun == 1 && groupIndexToRun == 1) {
                withContext(Dispatchers.Main) {
                    findElementById("NextWeek").click()
                }
                Log.d(TAG, "delay 1")
                delay(DELAY)
            }
            withContext(Dispatchers.Main) {
                // extract table
                val masterDivXpath = "//*[@id=\"myTabContent6\"]/div[2]"
                val parsed = Jsoup.parse(html)
                val masterDiv = parsed.selectXpath(masterDivXpath)[0]
                masterDiv.children().drop(2).forEach { masterDivChild ->
                    val header = masterDivChild.children()[0]
                    val mealName = header.child(0).text()
                    masterDivChild.children().drop(1).forEach { day ->
                        val date = day.getElementById("DateDiv")?.text()
                        val foods = day.getElementsByClass("reserveFoodFoodDiv")
                        foods.forEachIndexed { index, foodItem ->
                            val checked = foodItem.getElementsByTag("input")[0]
                                .attr("checked") == "checked"
                            val foodNameLabels = foodItem.getElementsByTag("label")
                            val label = foodNameLabels[0].ownText()
                            val record = ReserveRecord(
                                date!!.substringAfter("-").toJalali().toLongFormat(),
                                mealName,
                                groupIndexToRun - 1,
                                restaurantNames[restaurantIndexToRun - 1],
                                label,
                                checked
                            )
                            theList.add(record)
//                            Log.d(TAG, "DiningScrapper->extract: $record")
                            Log.d(TAG, "$date: $mealName: $checked")
                        }
                    }
                }
                if (restaurantIndexToRun < restaurants.size)
                    nextRestaurant()
                else
                    if (groupIndexToRun < groups.size)
                        nextGroup()
                    else
                        onFinish?.let { it(theList) }
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