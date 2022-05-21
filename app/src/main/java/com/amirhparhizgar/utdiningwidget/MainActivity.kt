package com.amirhparhizgar.utdiningwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.amirhparhizgar.utdiningwidget.ui.theme.UTDiningWidgetTheme
import com.daandtu.webscraper.WebScraper


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webScraper = WebScraper(this)
        webScraper.setUserAgentToDesktop(true) //default: false
        webScraper.setLoadImages(true) //default: false
        webScraper.loadURL("https://time.ir")

        setContent {
            UTDiningWidgetTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Row {
                        Greeting("Android")
                        AndroidView(factory = {
                            webScraper.view
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    UTDiningWidgetTheme {
        Greeting("Android")
    }
}