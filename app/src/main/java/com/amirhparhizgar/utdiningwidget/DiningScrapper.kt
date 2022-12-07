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

class Group(val id: String, val name: String)
class Restaurant(val id: String, val name: String)

@SuppressLint("SetJavaScriptEnabled")
class DiningScrapper(
    context: Context
) : WebScraper(context) {
    var nextWeek: Boolean = false
    private lateinit var username: String
    private lateinit var password: String
    private val dataStore = context.dataStore.data
    private var onPageLoadedListener: (() -> Unit)? = null

    private val theList = mutableListOf<ReserveRecord>()

    private val webView
        get() = view as WebView

    init {
        webView.webViewClient = object : WebViewClient() {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String): WebResourceResponse? {
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

    suspend fun start(): List<ReserveRecord> {
        clearAll()
        login()
        loadReserve()
        return theList
    }

    private suspend fun login() {
        dataStore.map {
            username = it[USERNAME_KEY] ?: ""
            password = it[PASSWORD_KEY] ?: ""
        }.first()

        loadURLAndWait("https://dining2.ut.ac.ir")
        val usernameField = findElementById("username")
        val passwordField = findElementById("password")
        val submit = findElementByName("submit")
        usernameField.text = username
        passwordField.text = password
        doAndAwaitLoad {
            submit.click()
        }
    }

    private suspend fun loadReserve() {
        loadURLAndWait("https://dining2.ut.ac.ir/Reserves")
        val personGroup = Jsoup.parse(html).getElementById(PERSON_GROUP_ID)
        val groups = personGroup?.children()?.map {
            Group(it.attr("value"), it.ownText())
        }?.filter { it.id != "0" } ?: return

        groups.forEachIndexed { i, group ->
            if (i > 0)
                setGroup(group)
            extractGroup(group, i == 0)
        }
    }

    private suspend fun setGroup(group: Group) {
        Log.d(TAG, "DiningScrapper->nextGroup: selecting group $group")
        webView.evaluateJavascript(
            "element692 = " +
                    "document.getElementById(\"$PERSON_GROUP_ID\");" +
                    " element692.value = \"${group.id}\";"
        )
        webView.evaluateJavascript(
            "getRest(false);"
        )
        Log.d(TAG, "delay 3")
        waitForSpinner()
    }

    private suspend fun extractGroup(group: Group, isFirstGroup: Boolean) {
        val restaurantSelect = Jsoup.parse(getHtmlInMain()).getElementById(RESTAURANT)
        val restaurants = restaurantSelect?.children()?.map {
            Restaurant(it.attr("value"), it.ownText())
        }?.filter { it.id != "0" } ?: throw Exception("empty restaurant!")

        restaurants.forEach { restaurant ->
            if (isFirstGroup) {
                setRestaurant(restaurant)
                if (nextWeek)
                    goNextWeek()
            }
            extract(group, restaurant)
        }
    }

    private suspend fun goNextWeek() {
        withContext(Dispatchers.Main) {
            findElementById("NextWeek").click()
        }
        Log.d(TAG, "delay next week")
        waitForSpinner()
    }

    private suspend fun extract(group: Group, restaurant: Restaurant) {
        Log.d(TAG, "delay extract")
        waitForSpinner()
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
                foods.forEach { foodItem ->
                    val checked = foodItem.getElementsByTag("input")[0]
                        .attr("checked") == "checked"
                    val foodNameLabels = foodItem.getElementsByTag("label")
                    val label = foodNameLabels[0].ownText()
                    val record = ReserveRecord(
                        date!!.substringAfter("-").toJalali().toLongFormat(),
                        mealName,
                        group.id.toInt(),
                        restaurant.name,
                        label,
                        checked
                    )
                    theList.add(record)
                    Log.d(TAG, "$date: $mealName: $checked")
                }
            }
        }
    }

    private suspend fun setRestaurant(restaurant: Restaurant) {
        webView.evaluateJavascript(
            "element492 = " +
                    "document.getElementById(\"$RESTAURANT\");" +
                    " element492.value = \"${restaurant.id}\";"
        )
        webView.evaluateJavascript(
            "getReservePage();"
        )
        waitForSpinner()
    }

    private suspend fun WebView.evaluateJavascript(script: String) {
        withContext(Dispatchers.Main) {
            suspendCoroutine<String> { continuation ->
                evaluateJavascript(
                    script
                ) { response ->
                    continuation.resume(response)
                }
            }
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
                if (n == 0)
                    delay(200)
                else {
                    delay(200)
                    break
                }
            }
            n++
            delay(100)
        }
    }

    private suspend fun getHtmlInMain() = withContext(Dispatchers.Main) {
        html
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
}