package com.amirhparhizgar.utdiningwidget.domain

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.amirhparhizgar.utdiningwidget.Bridge
import com.amirhparhizgar.utdiningwidget.data.model.ReserveRecord
import com.amirhparhizgar.utdiningwidget.getNextWeek2FuncDef
import com.amirhparhizgar.utdiningwidget.getReservePage2FuncDef
import com.amirhparhizgar.utdiningwidget.getRest2FuncDef
import com.amirhparhizgar.utdiningwidget.ui.*
import com.daandtu.webscraper.WebScraper
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


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
    private val bridge = Bridge()

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
        withContext(Dispatchers.Main) {
            webView.addJavascriptInterface(bridge, "bridge")
        }

        loadURLAndWait("https://dining2.ut.ac.ir/Reserves")
        var groups: List<Group>? = null
        while (groups == null) {
            val personGroup = Jsoup.parse(getHtmlInMain()).getElementById(PERSON_GROUP_ID)
            groups = personGroup?.children()?.map {
                Group(it.attr("value"), it.ownText())
            }?.filter { it.id != "0" }
        }

        webView.evaluateJavascriptSuspend(getRest2FuncDef)
        webView.evaluateJavascriptSuspend(getReservePage2FuncDef)
        webView.evaluateJavascriptSuspend(getNextWeek2FuncDef)

        groups.forEachIndexed { i, group ->
            extractGroup(group, i == 0)
        }
    }

    private suspend fun extractGroup(group: Group, isFirstGroup: Boolean) {
        val restaurantsJson = withContext(Dispatchers.Main) {
            suspendCoroutine { cont ->
                bridge.listener = {
                    cont.resume(it)
                }
                webView.evaluateJavascript("getRest2(${group.id});", null)
            }
        }
        val restaurants = mutableListOf<Restaurant>()
        Log.d(TAG, "DiningScrapper->loadReserve: res=$restaurants")
        //        [{"id":30,"value":"دانشکده-سلف فنی1-16 آذر -برادران-کارکنان-برادران","value2":null,"HasError":false}]
        val json = JsonParser.parseString(restaurantsJson)
        json.asJsonArray.forEach {
            it.asJsonObject.run {
                restaurants.add(
                    Restaurant(
                        id = get("id").asInt.toString(),
                        name = get("value").asString,
                    )
                )
            }
        }

        restaurants.forEach { restaurant ->
            Log.d(TAG, "DiningScrapper: want to extract restaurant ${restaurant.name}")
            val reserveHtml = withContext(Dispatchers.Main) {
                suspendCoroutine { cont ->
                    bridge.listener = {
                        cont.resume(it)
                    }
                    val script = if (isFirstGroup and nextWeek)
                        "getNextWeek2(${group.id}, ${restaurant.id});"
                    else
                        "getReservePage2(${group.id}, ${restaurant.id});"
                    webView.evaluateJavascript(script, null)
                }
            }
            extract(group, restaurant, reserveHtml)
        }
    }

    private fun extract(group: Group, restaurant: Restaurant, reserveHtml: String) {
        val masterDivXpath = "//*[@id=\"myTabContent6\"]/div[2]"
        val masterDiv = Jsoup.parse(reserveHtml).selectXpath(masterDivXpath)[0]
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

    private suspend fun WebView.evaluateJavascriptSuspend(script: String): String {
        return withContext(Dispatchers.Main) {
            suspendCoroutine { continuation ->
                evaluateJavascript(
                    script
                ) { response ->
                    if (response == null)
                        continuation.resume("")
                    else
                        continuation.resume(response)
                }
            }
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