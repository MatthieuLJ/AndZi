package com.matthieu.zi_app.ui.podcast.catalog

import com.matthieu.zi_app.data.local.entity.PodcastChannelEntity
import com.matthieu.zi_app.data.local.entity.PodcastEpisodeEntity

data class EpisodeItemUiState(
    val episode: PodcastEpisodeEntity,
    val isDownloaded: Boolean,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val targetHskLevel: Int = 0,
    val totalWords: Int = 0,
    val knownWords: Int = 0,
    val masteryRatio: Float = 0f
)

data class PodcastCatalogUiState(
    val channels: List<PodcastChannelEntity> = emptyList(),
    val selectedChannelId: Int? = 1,
    val searchQuery: String = "",
    val onlyDownloaded: Boolean = false,
    val totalDownloadedCount: Int = 0,
    val channelEpisodeCounts: Map<Int, Int> = emptyMap(),
    val selectedHskLevel: Int? = null,
    val hskCounts: Map<Int, Int> = emptyMap(),
    val episodes: List<EpisodeItemUiState> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

