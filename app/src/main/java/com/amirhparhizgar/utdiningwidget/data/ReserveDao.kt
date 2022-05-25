package com.amirhparhizgar.utdiningwidget.data

import androidx.room.*
import com.amirhparhizgar.utdiningwidget.ReserveRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ReserveDao {
    @Query("SELECT * FROM ReserveRecord")
    fun loadAll(): Flow<List<ReserveRecord>>

    @Query("SELECT * FROM ReserveRecord WHERE date IN (:date)")
    fun loadAllByDate(vararg date: String): List<ReserveRecord>?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg reserves: ReserveRecord)

    @Delete
    fun delete(reserveRecord: ReserveRecord)

}