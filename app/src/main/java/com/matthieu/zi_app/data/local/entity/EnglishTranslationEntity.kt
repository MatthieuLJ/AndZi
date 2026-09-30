package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "eword",
    foreignKeys = [
        ForeignKey(
            entity = ChineseWordEntity::class,
            parentColumns = ["cword_id"],
            childColumns = ["cword_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cword_id"])
    ]
)
data class EnglishTranslationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "eng_id")
    val engId: Long = 0,

    @ColumnInfo(name = "cword_id")
    val cwordId: Long,

    @ColumnInfo(name = "translation")
    val translation: String
)
