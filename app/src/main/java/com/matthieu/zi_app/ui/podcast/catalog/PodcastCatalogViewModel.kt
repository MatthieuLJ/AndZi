package com.matthieu.zi_app.ui.podcast.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.data.local.entity.PodcastEpisodeEntity
import com.matthieu.zi_app.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private data class FilterParams(
    val selectedChannelId: Int?,
    val query: String,
    val onlyDownloaded: Boolean
)

private data class SyncParams(
    val downloading: Map<Int, Float>,
    val isRefreshing: Boolean,
    val errorMessage: String?
)

class PodcastCatalogViewModel(
    private val podcastRepository: PodcastRepository,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000)
) : ViewModel() {

    private val _selectedChannelId = MutableStateFlow<Int?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _onlyDownloaded = MutableStateFlow(false)
    private val _downloadingEpisodes = MutableStateFlow<Map<Int, Float>>(emptyMap())
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    init {
        checkAndSyncChannels()
    }

    private fun checkAndSyncChannels() {
        viewModelScope.launch {
            try {
                val slowChineseCount = podcastRepository.getEpisodeCountForChannel(1)
                val readingChinaCount = podcastRepository.getEpisodeCountForChannel(2)
                if (slowChineseCount == 0 && readingChinaCount == 0) {
                    _isRefreshing.value = true
                    _errorMessage.value = null
                    val res = podcastRepository.syncChannelEpisodes(1)
                    if (res.isFailure) {
                        _errorMessage.value = res.exceptionOrNull()?.localizedMessage
                    }
                    _isRefreshing.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage
                _isRefreshing.value = false
            }
        }
    }

    private val filterParams = combine(
        _selectedChannelId,
        _searchQuery,
        _onlyDownloaded
    ) { selectedChannelId, query, onlyDownloaded ->
        FilterParams(selectedChannelId, query, onlyDownloaded)
    }

    private val syncParams = combine(
        _downloadingEpisodes,
        _isRefreshing,
        _errorMessage
    ) { downloading, isRefreshing, errorMessage ->
        SyncParams(downloading, isRefreshing, errorMessage)
    }

    val uiState: StateFlow<PodcastCatalogUiState> = combine(
        podcastRepository.getAllChannels(),
        podcastRepository.getAllEpisodes(),
        filterParams,
        syncParams
    ) { channels, allEpisodes, filter, sync ->
        val filtered = allEpisodes.filter { ep ->
            val matchesChannel = filter.selectedChannelId == null || ep.channelId == filter.selectedChannelId
            val matchesQuery = filter.query.isBlank() || ep.title.contains(filter.query, ignoreCase = true)
            val isDownloaded = ep.downloadedPath != null && File(ep.downloadedPath).exists()
            val matchesDownloaded = !filter.onlyDownloaded || isDownloaded
            matchesChannel && matchesQuery && matchesDownloaded
        }.map { ep ->
            val isDownloaded = ep.downloadedPath != null && File(ep.downloadedPath).exists()
            val progress = sync.downloading[ep.episodeId]
            EpisodeItemUiState(
                episode = ep,
                isDownloaded = isDownloaded,
                isDownloading = progress != null,
                downloadProgress = progress ?: 0f
            )
        }

        PodcastCatalogUiState(
            channels = channels,
            selectedChannelId = filter.selectedChannelId,
            searchQuery = filter.query,
            onlyDownloaded = filter.onlyDownloaded,
            episodes = filtered,
            isLoading = false,
            isRefreshing = sync.isRefreshing,
            errorMessage = sync.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = sharingStarted,
        initialValue = PodcastCatalogUiState()
    )

    fun selectChannel(channelId: Int?) {
        _selectedChannelId.value = channelId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleOnlyDownloaded(enabled: Boolean) {
        _onlyDownloaded.value = enabled
    }

    fun refreshCurrentChannel() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                _errorMessage.value = null
                val channelId = _selectedChannelId.value ?: 1
                val result = podcastRepository.syncChannelEpisodes(channelId)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Sync failed"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Sync failed"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun downloadEpisode(episode: PodcastEpisodeEntity) {
        if (_downloadingEpisodes.value.containsKey(episode.episodeId)) return

        viewModelScope.launch {
            _downloadingEpisodes.update { it + (episode.episodeId to 0f) }
            podcastRepository.downloadEpisodeAudio(episode) { progress ->
                _downloadingEpisodes.update { it + (episode.episodeId to progress) }
            }
            _downloadingEpisodes.update { it - episode.episodeId }
        }
    }

    fun deleteDownloadedAudio(episodeId: Int) {
        viewModelScope.launch {
            podcastRepository.deleteDownloadedAudio(episodeId)
        }
    }

    companion object {
        fun provideFactory(podcastRepository: PodcastRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PodcastCatalogViewModel(podcastRepository) as T
                }
            }
    }
}
