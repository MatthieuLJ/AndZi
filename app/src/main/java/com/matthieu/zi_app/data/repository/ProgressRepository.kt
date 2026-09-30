package com.matthieu.zi_app.data.repository

import com.matthieu.zi_app.data.local.dao.LeitnerDao
import com.matthieu.zi_app.data.local.dao.ProgressDao
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ProgressRepository(
    private val progressDao: ProgressDao,
    private val leitnerDao: LeitnerDao? = null
) {

    fun getAllProgressFlow(): Flow<List<ProgressRecordEntity>> {
        return progressDao.getAllProgressFlow()
    }

    suspend fun getAllProgress(): List<ProgressRecordEntity> = withContext(Dispatchers.IO) {
        progressDao.getAllProgress()
    }

    suspend fun getProgressInRange(minDate: Long, maxDate: Long, maxZoom: Int): List<ProgressRecordEntity> = withContext(Dispatchers.IO) {
        progressDao.getProgressInRange(minDate, maxDate, maxZoom)
    }

    suspend fun recordProgressSnapshot(
        totalBox1: Int,
        totalBox2: Int,
        totalBox3: Int,
        totalBox4: Int,
        totalBox5: Int,
        expiredBox2: Int,
        expiredBox3: Int,
        expiredBox4: Int,
        expiredBox5: Int,
        date: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val record = ProgressRecordEntity(
            date = date,
            totalBox1 = totalBox1,
            totalBox2 = totalBox2,
            totalBox3 = totalBox3,
            totalBox4 = totalBox4,
            totalBox5 = totalBox5,
            expiredBox2 = expiredBox2,
            expiredBox3 = expiredBox3,
            expiredBox4 = expiredBox4,
            expiredBox5 = expiredBox5,
            zoom = -1 // Marked for downsample distribution
        )
        progressDao.insertProgress(record)
    }

    suspend fun distributeDownsampling(
        minDate: Long,
        maxDate: Long,
        maxNum: Int = 30,
        maxZoom: Int = 3
    ) = withContext(Dispatchers.IO) {
        if (maxDate <= minDate) return@withContext
        progressDao.resetAllZoom()
        progressDao.updateZoom(minDate, 0)
        progressDao.updateZoom(maxDate, 0)

        distributeLevel(minDate, maxDate, maxNum, zoom = 0, maxZoom = maxZoom - 1)

        progressDao.setRemainingUnassignedZoom(maxZoom + 1)
    }

    private suspend fun distributeLevel(
        minDate: Long,
        maxDate: Long,
        maxNum: Int,
        zoom: Int,
        maxZoom: Int
    ) {
        if (maxDate <= minDate || maxNum <= 0) return
        val increment = (maxDate - minDate) / maxNum

        for (i in 0..maxNum) {
            val targetDate = minDate + (i * increment)
            if (i > 0 && i < maxNum) {
                val candidates = progressDao.getUnassignedProgressInRange(
                    targetDate - increment / 2,
                    targetDate + increment / 2
                )
                val closest = candidates.minByOrNull { abs(it.date - targetDate) }
                if (closest != null) {
                    progressDao.updateZoom(closest.date, zoom)
                }
            }

            if (zoom < maxZoom) {
                when (i) {
                    0 -> distributeLevel(minDate, targetDate, 2, zoom + 1, maxZoom)
                    maxNum -> distributeLevel(targetDate - increment, maxDate, 2, zoom + 1, maxZoom)
                    else -> distributeLevel(targetDate - increment, targetDate, 2, zoom + 1, maxZoom)
                }
            }
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        progressDao.clearAll()
    }
}
