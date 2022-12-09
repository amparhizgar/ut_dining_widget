package com.amirhparhizgar.utdiningwidget.data

import androidx.room.*
import com.amirhparhizgar.utdiningwidget.data.model.ReserveRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ReserveDao {
    @Query("SELECT * FROM ReserveRecord")
    fun loadAll(): Flow<List<ReserveRecord>>

    @Query("SELECT * FROM ReserveRecord WHERE date IN (:date) AND reserved == 1")
    fun loadAllByDateReserved(vararg date: Long): List<ReserveRecord>

    @Query("SELECT * FROM ReserveRecord WHERE date >= :date AND reserved == 1")
    fun loadAllAfterReserved(vararg date: Long): Flow<List<ReserveRecord>>

    @Query("SELECT * FROM ReserveRecord WHERE date >= :date")
    fun loadAllAfter(date: Long): Flow<List<ReserveRecord>>

    @Query("SELECT * FROM ReserveRecord WHERE date >= :start AND date <= :end")
    fun loadAllBetween(start: Long, end: Long): List<ReserveRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg reserves: ReserveRecord)

    @Delete
    fun delete(reserveRecord: ReserveRecord)

}