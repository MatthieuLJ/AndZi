package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "cword_segment",
    primaryKeys = ["cword_id", "pos"],
    foreignKeys = [
        ForeignKey(
            entity = ChineseWordEntity::class,
            parentColumns = ["cword_id"],
            childColumns = ["cword_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CharPinyinEntity::class,
            parentColumns = ["char_pinyin_id"],
            childColumns = ["char_pinyin_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pos"], name = "cword_segment_pos"),
        Index(value = ["char_pinyin_id"])
    ]
)
data class WordSegmentEntity(
    @ColumnInfo(name = "cword_id")
    val cwordId: Long,

    @ColumnInfo(name = "char_pinyin_id")
    val charPinyinId: Long,

    @ColumnInfo(name = "pos")
    val pos: Int
)
