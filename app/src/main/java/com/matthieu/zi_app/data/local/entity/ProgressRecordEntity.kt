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

    @ColumnInfo(name = "expired_box_2")
    val expiredBox2: Int? = 0,

    @ColumnInfo(name = "expired_box_3")
    val expiredBox3: Int? = 0,

    @ColumnInfo(name = "expired_box_4")
    val expiredBox4: Int? = 0,

    @ColumnInfo(name = "expired_box_5")
    val expiredBox5: Int? = 0,

    @ColumnInfo(name = "zoom", defaultValue = "0")
    val zoom: Int? = 0
) {
    val b1: Int get() = totalBox1 ?: 0
    val b2: Int get() = totalBox2 ?: 0
    val b3: Int get() = totalBox3 ?: 0
    val b4: Int get() = totalBox4 ?: 0
    val b5: Int get() = totalBox5 ?: 0
    val e2: Int get() = expiredBox2 ?: 0
    val e3: Int get() = expiredBox3 ?: 0
    val e4: Int get() = expiredBox4 ?: 0
    val e5: Int get() = expiredBox5 ?: 0
    val z: Int get() = zoom ?: 0
}
