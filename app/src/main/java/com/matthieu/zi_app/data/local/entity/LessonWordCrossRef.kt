package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "lesson_word",
    primaryKeys = ["eng_id", "lesson_id"],
    foreignKeys = [
        ForeignKey(
            entity = EnglishTranslationEntity::class,
            parentColumns = ["eng_id"],
            childColumns = ["eng_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["lesson_id"],
            childColumns = ["lesson_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["lesson_id"]),
        Index(value = ["eng_id"])
    ]
)
data class LessonWordCrossRef(
    @ColumnInfo(name = "eng_id")
    val engId: Long,

    @ColumnInfo(name = "lesson_id")
    val lessonId: Long
)
