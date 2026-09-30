package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cword",
    indices = [
        Index(value = ["chinese", "pinyin"], unique = true),
        Index(value = ["chinese"], name = "cword_chinese"),
        Index(value = ["pinyin"], name = "cword_pinyin")
    ]
)
data class ChineseWordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "cword_id")
    val cwordId: Long = 0,

    @ColumnInfo(name = "chinese")
    val chinese: String,

    @ColumnInfo(name = "pinyin")
    val pinyin: String,

    @ColumnInfo(name = "length", defaultValue = "0")
    val length: Int = 0
)
