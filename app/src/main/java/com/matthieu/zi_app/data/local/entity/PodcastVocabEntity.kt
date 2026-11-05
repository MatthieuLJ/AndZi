package com.matthieu.zi_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcast_vocab",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEpisodeEntity::class,
            parentColumns = ["episode_id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["episode_id"], name = "idx_vocab_episode"),
        Index(value = ["chinese"], name = "idx_vocab_chinese")
    ]
)
data class PodcastVocabEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "vocab_id")
    val vocabId: Int = 0,
    @ColumnInfo(name = "episode_id")
    val episodeId: Int,
    @ColumnInfo(name = "chinese")
    val chinese: String,
    @ColumnInfo(name = "pinyin")
    val pinyin: String = "",
    @ColumnInfo(name = "translation")
    val translation: String = "",
    @ColumnInfo(name = "hsk_level")
    val hskLevel: Int = 0
)

