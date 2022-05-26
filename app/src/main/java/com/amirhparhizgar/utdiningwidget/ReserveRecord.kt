package com.amirhparhizgar.utdiningwidget

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(primaryKeys = ["date", "meal"])
data class ReserveRecord(
    val date: Long,
    val meal: String,
    val name: String
)
