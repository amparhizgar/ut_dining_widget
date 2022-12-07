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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val RESTAURANT = "Restaurant"
const val PERSON_GROUP_ID = "PersonGroup"
const val DELAY: Long = 1000

@SuppressLint("SetJavaScriptEnabled")
class DiningScrapper(
    context: Context
) : WebScraper(context) {
    var nextWeek: Boolean = false
    private lateinit var username: String
    private lateinit var password: String
    private val dataStore = context.dataStore.data
    private var onPageLoadedListener: (() -> Unit)? = null

    init {
        webView.webViewClient = object : WebViewClient() {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String): WebResourceResponse? {
                Log.d(TAG, "shouldInterceptRequest: $url")
                @Suppress("DEPRECATION")
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
            }
        }

        setUserAgentToDesktop(true) //default: false
        setLoadImages(false) //default: false
        webView.settings.javaScriptEnabled = true
    }

    private var groupIndexToRun = 0
    private var restaurantIndexToRun = 0

    private val theList = mutableListOf<ReserveRecord>()

    suspend fun start(): List<ReserveRecord> {
        clearAll()
        dataStore.map {
            username = it[USERNAME_KEY] ?: ""
            password = it[PASSWORD_KEY] ?: ""
        }.first()

        groupIndexToRun = 0
        restaurantIndexToRun = 0
        loadURLAndWait("https://dining2.ut.ac.ir")
        val usernameField = findElementById("username")
        val passwordField = findElementById("password")
        val submit = findElementByName("submit")
        usernameField.text = username
        passwordField.text = password
        doAndAwaitLoad {
            submit.click()
        }
        loadReserve()
        return theList
    }

    private suspend fun loadURLAndWait(URL: String) {
        doAndAwaitLoad {
            loadURL(URL)
        }
    }

    private suspend fun doAndAwaitLoad(runnable: () -> Unit) {
        withContext(Dispatchers.Main) {
            suspendCoroutine<Unit> {
                onPageLoadedListener = {
                    it.resume(Unit)
                }
                runnable()
            }
        }
    }

    private suspend fun loadReserve() {
        loadURLAndWait("https://dining2.ut.ac.ir/Reserves")
        onReservePageLoaded()
    }


    private var groups = emptyList<String>()
    private var restaurants = emptyList<String>()
    private var restaurantNames = emptyList<String>()

    private suspend fun onReservePageLoaded() {
        val personGroup = Jsoup.parse(html).getElementById(PERSON_GROUP_ID)
        groups = personGroup?.children()?.map {
            it.attr("value")
        }?.filter { it != "0" } ?: return

        nextGroup()
    }

    private suspend fun WebView.evaluateJavascript(script: String) {
        withContext(Dispatchers.Main) {
            suspendCoroutine<String> { continuetion ->
                evaluateJavascript(
                    script
                ) { response ->
                    continuetion.resume(response)
                }
            }
        }
    }

    private suspend fun nextGroup() {
        val group = groups[groupIndexToRun]
        groupIndexToRun += 1
        Log.d(TAG, "DiningScrapper->nextGroup: selecting group $group")
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "element692 = " +
                        "document.getElementById(\"$PERSON_GROUP_ID\");" +
                        " element692.value = \"$group\";"
            )
            onGroupSelected()
        }
    }

    private suspend fun waitForSpinner() {
        var n = 0
        while (true) {
            val spinner = Jsoup.parse(getHtmlInMain()).getElementById("ajaxLoader")!!
            val isVisible = spinner.attr("style")
                .contains("grid") // when there is 'grid' it's shown and when it's 'none' it's invisible
            Log.d(TAG, "DiningScrapper->waitForSpinner: spinner is visible: $isVisible")
            if (!isVisible) {
//                assert(n > 0)
                break
            }
            n++
            delay(100)
        }
    }

    private suspend fun onGroupSelected() {
        webView.evaluateJavascript(
            "getRest(false);"
        )
        Log.d(TAG, "delay 3")
        waitForSpinner()
        val restaurantSelect = Jsoup.parse(getHtmlInMain()).getElementById(RESTAURANT)
        restaurants = restaurantSelect?.children()?.map {
            it.attr("value")
        }?.filter { it != "0" } ?: throw Exception("empty restaurant!")

        restaurantNames = restaurantSelect.children().map {
            it.ownText()
        }.filter { it != "0" }

        restaurantIndexToRun = 0
        nextRestaurant()

    }

    private suspend fun nextRestaurant() {
        val restaurant = restaurants[restaurantIndexToRun]
        restaurantIndexToRun += 1
        Log.d(TAG, "DiningScrapper->nextRestaurant: selecting restaurant: $restaurant")
        if (restaurantIndexToRun > 1)
            setRestaurant(restaurant)
        extract()
    }

    private suspend fun getHtmlInMain() = withContext(Dispatchers.Main) {
        html
    }

    private suspend fun extract() {
        Log.d(TAG, "delay 2")
        delay(DELAY)
        if (nextWeek && restaurantIndexToRun == 1 && groupIndexToRun == 1) {
            withContext(Dispatchers.Main) {
                findElementById("NextWeek").click()
            }
            Log.d(TAG, "delay 1")
            delay(DELAY)
        }
        // extract table
        val masterDivXpath = "//*[@id=\"myTabContent6\"]/div[2]"
        val parsed = Jsoup.parse(getHtmlInMain())
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
    }

    private suspend fun setRestaurant(restaurant: String) {
        webView.evaluateJavascript(
            "element492 = " +
                    "document.getElementById(\"$RESTAURANT\");" +
                    " element492.value = \"${restaurant}\";"
        )
        webView.evaluateJavascript(
            "getReservePage();"
        )
    }

    private val webView
        get() = view as WebView
}