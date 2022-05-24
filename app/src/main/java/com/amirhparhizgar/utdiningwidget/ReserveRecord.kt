package com.amirhparhizgar.utdiningwidget

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(primaryKeys = ["date", "meal"])
data class ReserveRecord(
    val date: String,
    val meal: String,
    val name: String
)
