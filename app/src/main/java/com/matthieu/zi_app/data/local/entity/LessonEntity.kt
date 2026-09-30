package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lessons",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class LessonEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "lesson_id")
    val lessonId: Long = 0,

    @ColumnInfo(name = "list_order", defaultValue = "0")
    val listOrder: Int = 0,

    @ColumnInfo(name = "name")
    val name: String
)
