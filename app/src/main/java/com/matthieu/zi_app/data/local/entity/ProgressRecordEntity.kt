package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "progress",
    indices = [
        Index(value = ["date"], name = "progress_date")
    ]
)
data class ProgressRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "total_box_1")
    val totalBox1: Int? = 0,

    @ColumnInfo(name = "total_box_2")
    val totalBox2: Int? = 0,

    @ColumnInfo(name = "total_box_3")
    val totalBox3: Int? = 0,

    @ColumnInfo(name = "total_box_4")
    val totalBox4: Int? = 0,

    @ColumnInfo(name = "total_box_5")
    val totalBox5: Int? = 0,

    @ColumnInfo(name = "total_box_6", defaultValue = "0")
    val totalBox6: Int? = 0,

    @ColumnInfo(name = "total_box_7", defaultValue = "0")
    val totalBox7: Int? = 0,

    @ColumnInfo(name = "total_box_8", defaultValue = "0")
    val totalBox8: Int? = 0,

    @ColumnInfo(name = "total_box_9", defaultValue = "0")
    val totalBox9: Int? = 0,

    @ColumnInfo(name = "total_box_10", defaultValue = "0")
    val totalBox10: Int? = 0,

    @ColumnInfo(name = "expired_box_2")
    val expiredBox2: Int? = 0,

    @ColumnInfo(name = "expired_box_3")
    val expiredBox3: Int? = 0,

    @ColumnInfo(name = "expired_box_4")
    val expiredBox4: Int? = 0,

    @ColumnInfo(name = "expired_box_5")
    val expiredBox5: Int? = 0,

    @ColumnInfo(name = "expired_box_6", defaultValue = "0")
    val expiredBox6: Int? = 0,

    @ColumnInfo(name = "expired_box_7", defaultValue = "0")
    val expiredBox7: Int? = 0,

    @ColumnInfo(name = "expired_box_8", defaultValue = "0")
    val expiredBox8: Int? = 0,

    @ColumnInfo(name = "expired_box_9", defaultValue = "0")
    val expiredBox9: Int? = 0,

    @ColumnInfo(name = "expired_box_10", defaultValue = "0")
    val expiredBox10: Int? = 0,

    @ColumnInfo(name = "zoom", defaultValue = "0")
    val zoom: Int? = 0
) {
    val b1: Int get() = totalBox1 ?: 0
    val b2: Int get() = totalBox2 ?: 0
    val b3: Int get() = totalBox3 ?: 0
    val b4: Int get() = totalBox4 ?: 0
    val b5: Int get() = totalBox5 ?: 0
    val b6: Int get() = totalBox6 ?: 0
    val b7: Int get() = totalBox7 ?: 0
    val b8: Int get() = totalBox8 ?: 0
    val b9: Int get() = totalBox9 ?: 0
    val b10: Int get() = totalBox10 ?: 0
    val e2: Int get() = expiredBox2 ?: 0
    val e3: Int get() = expiredBox3 ?: 0
    val e4: Int get() = expiredBox4 ?: 0
    val e5: Int get() = expiredBox5 ?: 0
    val e6: Int get() = expiredBox6 ?: 0
    val e7: Int get() = expiredBox7 ?: 0
    val e8: Int get() = expiredBox8 ?: 0
    val e9: Int get() = expiredBox9 ?: 0
    val e10: Int get() = expiredBox10 ?: 0
    val z: Int get() = zoom ?: 0

    /**
     * Returns the count of cards in a specific Leitner box (0-9).
     * Handles backward compatibility for historical records where boxes 4-9 were aggregated in b5.
     */
    fun getBoxCount(box: Int): Int {
        val hasGranularUpperBoxes = (b6 + b7 + b8 + b9 + b10) > 0
        return when (box) {
            0 -> b1
            1 -> b2
            2 -> b3
            3 -> b4
            4 -> if (hasGranularUpperBoxes) b5 else b5
            5 -> b6
            6 -> b7
            7 -> b8
            8 -> b9
            9 -> b10
            else -> 0
        }
    }

    /**
     * Returns the count of expired cards in a specific Leitner box (1-9).
     */
    fun getExpiredBoxCount(box: Int): Int {
        val hasGranularUpperBoxes = (e6 + e7 + e8 + e9 + e10) > 0
        return when (box) {
            1 -> e2
            2 -> e3
            3 -> e4
            4 -> if (hasGranularUpperBoxes) e5 else e5
            5 -> e6
            6 -> e7
            7 -> e8
            8 -> e9
            9 -> e10
            else -> 0
        }
    }
}
