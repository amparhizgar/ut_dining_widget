package com.amirhparhizgar.utdiningwidget.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.amirhparhizgar.utdiningwidget.data.getDBInstance
import com.amirhparhizgar.utdiningwidget.data.model.sortBasedOnMeal
import kotlinx.coroutines.Dispatchers
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
        val context = LocalContext.current
        val db = runBlocking(Dispatchers.IO) {
            getDBInstance(context).dao()
        }

        val pDate = PersianDate(System.currentTimeMillis() + 0 * 60 * 60000)
        val dateFormat =
            PersianDateFormat("Y/m/d", PersianDateFormat.PersianDateNumberCharacter.ENGLISH)
        val dateStr = dateFormat.format(pDate)

        val list = runBlocking(Dispatchers.IO) {
            // 4 hours offset to determine day
            val list = db.loadAllByDateReserved(dateStr.toJalali().toLongFormat()).sortBasedOnMeal()
            list.map {
                return@map Item(it.meal, it.name.substringBefore("+"))
            }
        }

        Widget(
            pDate.dayName(),
            list,
        )
    }

    private val callback = actionRunCallback<WidgetClickAction>(
        parameters = actionParametersOf()
    )

    @Composable
    fun Widget(
        today: String,
        list: List<Item>,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth().cornerRadius(8.dp).clickable(callback)
                .background(Color(0f, 0f, 0f, 0.15f)).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = today,style = TextStyle(
                color = ColorProvider(
                    color = Color.LightGray
                )
            ), modifier = GlanceModifier.clickable(callback))
            list.forEach {
                FoodItem(it)
            }
        }
    }

    @Composable
    fun FoodItem(item: Item) {
        Row {
            Text(
                modifier = GlanceModifier.clickable(callback),
                text = item.meal.trim() + ": " + item.foodName.trim(),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ColorProvider(
                        color = Color.White
                    )
                )
            )
        }
    }
}

class WidgetClickAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}

class DiningWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DiningWidget()
}