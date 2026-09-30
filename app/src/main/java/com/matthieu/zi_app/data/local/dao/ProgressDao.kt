package com.matthieu.zi_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM progress ORDER BY date ASC")
    fun getAllProgressFlow(): Flow<List<ProgressRecordEntity>>

    @Query("SELECT * FROM progress ORDER BY date ASC")
    suspend fun getAllProgress(): List<ProgressRecordEntity>

    @Query("SELECT * FROM progress WHERE date >= :minDate AND date <= :maxDate AND zoom >= 0 AND zoom <= :maxZoom ORDER BY date ASC")
    suspend fun getProgressInRange(minDate: Long, maxDate: Long, maxZoom: Int): List<ProgressRecordEntity>

    @Query("SELECT * FROM progress WHERE zoom = -1")
    suspend fun getAllUnassignedProgress(): List<ProgressRecordEntity>

    @Query("SELECT * FROM progress WHERE date >= :minDate AND date <= :maxDate AND zoom = -1")
    suspend fun getUnassignedProgressInRange(minDate: Long, maxDate: Long): List<ProgressRecordEntity>

    @Query("UPDATE progress SET zoom = :zoom WHERE zoom = -1")
    suspend fun setRemainingUnassignedZoom(zoom: Int)

    @Query("UPDATE progress SET zoom = -1")
    suspend fun resetAllZoom()

    @Query("SELECT MIN(date) FROM progress")
    suspend fun getMinDate(): Long?

    @Query("SELECT MAX(date) FROM progress")
    suspend fun getMaxDate(): Long?

    @Query("SELECT COUNT(*) FROM progress")
    suspend fun getRecordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(record: ProgressRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressList(records: List<ProgressRecordEntity>)

    @Query("UPDATE progress SET zoom = :zoom WHERE date = :date")
    suspend fun updateZoom(date: Long, zoom: Int)

    @Query("DELETE FROM progress WHERE date < :cutoffDate")
    suspend fun deleteBefore(cutoffDate: Long)

    @Query("DELETE FROM progress")
    suspend fun clearAll()
}
