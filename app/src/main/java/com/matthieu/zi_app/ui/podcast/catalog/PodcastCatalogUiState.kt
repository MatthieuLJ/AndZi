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
    val selectedChannelId: Int? = null, // null means "All"
    val searchQuery: String = "",
    val onlyDownloaded: Boolean = false,
    val selectedHskLevel: Int? = null, // null means "All HSK"
    val episodes: List<EpisodeItemUiState> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

