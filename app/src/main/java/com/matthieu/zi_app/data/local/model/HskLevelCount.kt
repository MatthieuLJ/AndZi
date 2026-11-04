package com.matthieu.zi_app.data.local.model

import androidx.room.ColumnInfo

data class HskLevelCount(
    @ColumnInfo(name = "hsk_level")
    val hskLevel: Int,
    @ColumnInfo(name = "count")
    val count: Int
)

