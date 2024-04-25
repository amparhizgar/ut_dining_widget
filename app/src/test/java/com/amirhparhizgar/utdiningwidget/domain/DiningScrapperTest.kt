package com.amirhparhizgar.utdiningwidget.domain

import com.amirhparhizgar.utdiningwidget.data.model.ReserveRecord
import com.amirhparhizgar.utdiningwidget.data.model.uniconfig.QomUniConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File


class DiningScrapperTest {

    private val html = File("src/test/resources/myTabContent6.html").readText()

    @Test
    fun extracttest() = runBlocking {
        val reserves = mutableListOf<ReserveRecord>()
        extract(
            Group(id = "1", name = ""),
            Restaurant(id = "1", name = ""),
            html,
            QomUniConfig()
        ).toList(reserves)
        println(reserves)
    }
}