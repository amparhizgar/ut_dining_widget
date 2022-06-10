package com.amirhparhizgar.utdiningwidget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.amirhparhizgar.utdiningwidget.ReserveRecord


@Database(entities = [ReserveRecord::class], version = 4)
abstract class ReserveDatabase() : RoomDatabase() {
    abstract fun dao(): ReserveDao

}

private var instance: ReserveDatabase? = null
fun getDBInstance(context: Context): ReserveDatabase {
    if (instance == null)
        instance = Room.databaseBuilder(
            context, ReserveDatabase::class.java, "ReserveDatabase"
        ).fallbackToDestructiveMigration()
            .build()
    return instance!!
}