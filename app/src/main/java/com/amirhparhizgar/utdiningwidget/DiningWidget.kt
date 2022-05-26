package com.amirhparhizgar.utdiningwidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.amirhparhizgar.utdiningwidget.data.getDBInstance
import kotlinx.coroutines.runBlocking
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat


class DiningWidget : GlanceAppWidget() {
    data class Item(val meal: String, val foodName: String)

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        super.onDelete(context, glanceId)
    }

    @Composable
    override fun Content() {
        val db = getDBInstance(LocalContext.current).dao()
        val pDate = PersianDate(System.currentTimeMillis() - 4 * 60 * 60000)
        val dateFormat =
            PersianDateFormat("Y/m/d", PersianDateFormat.PersianDateNumberCharacter.ENGLISH)
        val dateStr = dateFormat.format(pDate)

        val list = runBlocking<List<Item>> {
            // 4 hours offset to determine day
            val list = db.loadAllByDate(dateStr)
            list?.map {
                return@map Item(it.meal, it.name.substringBefore("+"))
            } ?: listOf()
        }

        Widget(
            context = LocalContext.current,
            pDate.dayName(),
            list,
            modifier = GlanceModifier
        )
    }

    @Composable
    fun Widget(
        context: Context,
        today: String,
        list: List<Item>,
        modifier: GlanceModifier
    ) {
        Column {
            Text(text = today)
            list.forEach {
                FoodItem(it)
            }
        }
    }

    @Composable
    fun FoodItem(item: Item) {
        Row {
            Text(
                text = item.meal,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ColorProvider(
                        color = Color.White
                    )
                )
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = item.foodName,
                style = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    color = ColorProvider(
                        color = Color.White
                    )
                )
            )
        }
    }
}

class AddWaterClickAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        DiningWidget().update(context, glanceId)
    }
}

class DiningWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DiningWidget()
}