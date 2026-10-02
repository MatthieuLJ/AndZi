package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hsk_vocab",
    indices = [
        Index(value = ["hsk_level"], name = "idx_hsk_level"),
        Index(value = ["freq_rank"], name = "idx_hsk_freq")
    ]
)
data class HskVocabEntity(
    @PrimaryKey
    @ColumnInfo(name = "hanzi")
    val hanzi: String,

    @ColumnInfo(name = "hsk_level")
    val hskLevel: Int,

    @ColumnInfo(name = "hsk2_level", defaultValue = "0")
    val hsk2Level: Int = 0,

    @ColumnInfo(name = "freq_rank", defaultValue = "999999")
    val freqRank: Int = 999999,

    @ColumnInfo(name = "is_char", defaultValue = "0")
    val isChar: Int = 0
)
