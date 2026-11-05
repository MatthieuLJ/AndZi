package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcast_channel")
data class PodcastChannelEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "channel_id")
    val channelId: Int = 0,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "chinese_title")
    val chineseTitle: String,
    @ColumnInfo(name = "provider_type")
    val providerType: String = "BUNDLED",
    @ColumnInfo(name = "feed_url")
    val feedUrl: String? = null,
    @ColumnInfo(name = "intro_offset_ms")
    val introOffsetMs: Int = 0,
    @ColumnInfo(name = "outro_offset_ms")
    val outroOffsetMs: Int = 0,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true
)

