package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "char_pinyin",
    indices = [
        Index(value = ["char_pinyin_id"], name = "char_pinyin_id"),
        Index(value = ["hanzi"], name = "char_pinyin_hanzi"),
        Index(value = ["pinyin"], name = "char_pinyin_pinyin")
    ]
)
data class CharPinyinEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "char_pinyin_id")
    val charPinyinId: Long = 0,

    @ColumnInfo(name = "hanzi")
    val hanzi: String,

    @ColumnInfo(name = "pinyin")
    val pinyin: String
)
