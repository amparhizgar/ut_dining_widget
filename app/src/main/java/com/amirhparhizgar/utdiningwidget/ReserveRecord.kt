package com.amirhparhizgar.utdiningwidget

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(primaryKeys = ["date", "meal", "group", "restaurant", "name"])
data class ReserveRecord(
    val date: Long,
    val meal: String,
    val group: Int,
    val restaurant: String,
    val name: String,
    val reserved: Boolean
)

fun List<ReserveRecord>.sortBasedOnMeal(): List<ReserveRecord> {
    return this.sortedBy {
        when (it.meal) {
            "سحر" -> 1
            "سحری" -> 1
            "صبحانه" -> 2
            "ناهار" -> 3
            "شام" -> 4
            else -> 5
        }
    }
}
