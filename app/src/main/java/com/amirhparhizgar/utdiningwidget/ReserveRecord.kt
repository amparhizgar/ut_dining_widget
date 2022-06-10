package com.amirhparhizgar.utdiningwidget

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(primaryKeys = ["date", "meal", "group", "restaurant", "name"])
data class ReserveRecord(
    val date: Long,
    val meal: String,
    val group: Int,
    val restaurant: String,
    val index: Int,
    val name: String,
    val reserved: Boolean
)
