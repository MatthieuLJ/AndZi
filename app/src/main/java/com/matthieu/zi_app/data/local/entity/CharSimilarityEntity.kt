package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "char_similarity",
    primaryKeys = ["hanzi", "similar_hanzi"],
    indices = [
        Index(value = ["hanzi", "score"])
    ]
)
data class CharSimilarityEntity(
    @ColumnInfo(name = "hanzi") val hanzi: String,
    @ColumnInfo(name = "similar_hanzi") val similarHanzi: String,
    @ColumnInfo(name = "score") val score: Float,
    @ColumnInfo(name = "shared_component") val sharedComponent: String? = null
)

