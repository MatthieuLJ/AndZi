package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "leitner")
data class LeitnerRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "hanzi")
    val hanzi: String,

    @ColumnInfo(name = "box")
    val box: Int,

    @ColumnInfo(name = "exp_time")
    val expTime: Long,

    @ColumnInfo(name = "num_tested", defaultValue = "0")
    val numTested: Int = 0,

    @ColumnInfo(name = "num_wrong", defaultValue = "0")
    val numWrong: Int = 0,

    @ColumnInfo(name = "streak", defaultValue = "0")
    val streak: Int = 0,

    @ColumnInfo(name = "date_added")
    val dateAdded: Long = 0L
)
