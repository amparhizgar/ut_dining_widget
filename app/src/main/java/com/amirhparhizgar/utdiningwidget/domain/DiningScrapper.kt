package com.amirhparhizgar.utdiningwidget.domain

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.amirhparhizgar.utdiningwidget.data.model.ReserveRecord
import com.amirhparhizgar.utdiningwidget.ui.*
import com.daandtu.webscraper.WebScraper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import saman.zamani.persiandate.PersianDate
import java.util.Calendar
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


const val RESTAURANT = "Restaurant"
const val PERSON_GROUP_ID = "PersonGroup"

data class Group(val id: String, val name: String)
data class Restaurant(val id: String, val name: String)

@SuppressLint("SetJavaScriptEnabled")
class DiningScrapper @Inject constructor(
    @ApplicationContext context: Context,
    private val dataStore: DataStore<Preferences>
) : WebScraper(context) {
    var nextWeek: Boolean = false
    private lateinit var username: String
    private lateinit var password: String
    private var onPageLoadedListener: (() -> Unit)? = null
    private var onErrorListener: ((error: WebResourceError) -> Unit)? = null

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

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError
            ) {
                onErrorListener?.invoke(error)
            }
        }

        setUserAgentToDesktop(true) //default: false
        setLoadImages(false) //default: false
        webView.settings.javaScriptEnabled = true
    }

    suspend fun start(): List<ReserveRecord> {
        theList.removeAll { true }
        loadReserve()
        return theList
    }

    suspend fun login() {
        withContext(Dispatchers.Main) {
            clearAll()
        }
        dataStore.data.map {
            username = it[USERNAME_KEY] ?: ""
            password = it[PASSWORD_KEY] ?: ""
        }.first()

        loadURLAndWait("https://dining2.ut.ac.ir")
        val submit = withContext(Dispatchers.Main) {
            val usernameField = findElementById("username")
            val passwordField = findElementById("password")
            usernameField.text = username
            passwordField.text = password
            findElementByName("submit")
        }
        doAndAwaitLoad {
            submit.click()
        }
    }

    private suspend fun loadReserve() {
        loadURLAndWait("https://dining2.ut.ac.ir/Reserves")
        val personGroup = Jsoup.parse(getHtmlInMain()).getElementById(PERSON_GROUP_ID)
        val groups = personGroup?.children()?.map {
            Group(it.attr("value"), it.ownText())
        }?.filter { it.id != "0" } ?: return

        groups.forEachIndexed { i, group ->
            if (i > 0)
                setGroupAndWait(group)
            extractGroup(group, i == 0)
        }
    }

    private suspend fun setGroupAndWait(group: Group) {
        Log.d(TAG, "DiningScrapper->nextGroup: selecting group ${group.name}")

        val xPath = "//*[@id=\"Restaurant\"]"
        val firstId =
            Jsoup.parse(getHtmlInMain()).selectXpath(xPath).first()!!.children()[0].attr("value")

        webView.evaluateJavascript(
            "element692 = " +
                    "document.getElementById(\"$PERSON_GROUP_ID\");" +
                    " element692.value = \"${group.id}\";"
        )
        webView.evaluateJavascript(
            "getRest(false);"
        )

        while (true) {
            val selectElement = Jsoup.parse(getHtmlInMain()).selectXpath(xPath)
            val newFirstId = selectElement.first()!!.children()[0].attr("value")
            if (newFirstId != firstId)
                break
            else
                delay(200)
        }
    }


    private suspend fun extractGroup(group: Group, isFirstGroup: Boolean) {
        Log.d(TAG, "DiningScrapper->extractGroup: ${group.name}")
        val restaurantSelect = Jsoup.parse(getHtmlInMain()).getElementById(RESTAURANT)
        val restaurants = restaurantSelect?.children()?.map {
            Restaurant(it.attr("value"), it.ownText())
        }?.filter { it.id != "0" } ?: throw Exception("empty restaurant!")

        restaurants.forEach { restaurant ->
            if (isFirstGroup) {
                setRestaurant(restaurant)
                if (nextWeek) {
                    val currentWeek = getLoadedDateOrCurrent()
                    goNextWeek()
                    waitForWeek(currentWeek.apply { addDay(7) })
                }
            }
            Log.d(TAG, "DiningScrapper: want to extract restaurant ${restaurant.name}")
            waitForRestaurant(restaurant)
            extract(group, restaurant)
        }
    }

    private suspend fun goNextWeek() {
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript("getNextWeek();")
        }
    }

    private suspend fun extract(group: Group, restaurant: Restaurant) {
        // extract table
        val masterDivXpath = "//*[@id=\"myTabContent6\"]/div[2]"
        while (Jsoup.parse(getHtmlInMain()).selectXpath(masterDivXpath).size == 0) {
            delay(200)
        }
        val masterDiv = Jsoup.parse(getHtmlInMain()).selectXpath(masterDivXpath)[0]
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

    private suspend fun waitForRestaurant(restaurant: Restaurant) {
        val xPath = "//*[@id=\"myTabContent6\"]/div[2]/div[2]/div[2]/span[1]"
        while (true) {
            val span = Jsoup.parse(getHtmlInMain()).selectXpath(xPath)
            val text = span.text()
            val isLoaded =
                text.contains(restaurant.name)
            Log.d(TAG, "DiningScrapper->waitForRestaurant ($isLoaded) ${restaurant.name} VS $text")
            if (isLoaded)
                break
            else
                delay(200)
        }
    }

    private suspend fun getLoadedDateOrCurrent(): PersianDate {
        return runCatching {
            val saturdayXPath = "//*[@id=\"TopDateDiv\"]/div[2]"
            val saturday = Jsoup.parse(getHtmlInMain()).selectXpath(saturdayXPath)
            val indexOfSlash = saturday.text().indexOf('/')
            saturday.text().substring(indexOfSlash - 4, indexOfSlash + 6).toJalali()
        }.getOrDefault(PersianDate().apply {
            val dayOfWeek = (dayOfWeek() - Calendar.SATURDAY + 7) % 7
            addDay(-dayOfWeek.toLong())
        })
    }

    private suspend fun waitForWeek(weekSaturday: PersianDate) {
        Log.d(TAG, "DiningScrapper->waitForWeek: $weekSaturday")
        while (true) {
            val isLoaded = runCatching {
                val saturdayXPath = "//*[@id=\"TopDateDiv\"]/div[2]"
                val saturday = Jsoup.parse(getHtmlInMain()).selectXpath(saturdayXPath).text()
                val indexOfSlash = saturday.indexOf('/')
                val loadedSaturday =
                    saturday.substring(indexOfSlash - 4, indexOfSlash + 6).toJalali()
                Log.d(TAG, "DiningScrapper->waitForWeek: loadedWeek = $loadedSaturday")
                weekSaturday.toLongFormat() == loadedSaturday.toLongFormat()
            }.getOrDefault(false)
            if (isLoaded)
                break
            else
                delay(500)
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
            suspendCoroutine {
                onPageLoadedListener = {
                    it.resume(Unit)
                }
                onErrorListener = { error ->
                    onPageLoadedListener =
                        null // Otherwise it will throw "It's already resumed" exception
                    it.resumeWithException(
                        WebResourceException(
                            error.description.toString(),
                            error.errorCode
                        )
                    )
                }
                runnable()
            }
        }
    }
}

@Suppress("unused")
class WebResourceException(message: String, val code: Int) : Exception(message)