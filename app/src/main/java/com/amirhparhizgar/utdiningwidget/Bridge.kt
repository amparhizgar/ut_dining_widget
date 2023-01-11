package com.amirhparhizgar.utdiningwidget

import android.webkit.JavascriptInterface
import androidx.annotation.Keep

/**
 * Created by AmirHossein Parhizgar on 1/11/2023.
 */
class Bridge {
    var result: String? = null
    var listener: ((String) -> Unit)? = null

    @Suppress("unused")
    @JavascriptInterface
    @Keep
    fun putJsResult(result: String) {
        this.result = result
        listener?.let { it(result) }
    }
}