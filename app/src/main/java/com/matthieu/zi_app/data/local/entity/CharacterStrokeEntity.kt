package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_strokes")
data class CharacterStrokeEntity(
    @PrimaryKey
    @ColumnInfo(name = "hanzi") val hanzi: String,
    @ColumnInfo(name = "strokes") val strokes: String,
    @ColumnInfo(name = "medians") val medians: String,
    @ColumnInfo(name = "stroke_count") val strokeCount: Int = 0
)

