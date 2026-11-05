package com.matthieu.zi_app.ui.podcast.player

import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.entity.PodcastChannelEntity
import com.matthieu.zi_app.data.local.entity.PodcastEpisodeEntity
import com.matthieu.zi_app.data.local.entity.PodcastSentenceEntity
import com.matthieu.zi_app.data.local.entity.PodcastVocabEntity

enum class ListeningMode {
    READ_AND_LISTEN,
    BLIND
}

enum class VocabFilterTab {
    ALL,
    UNLEARNED,
    STUDIED
}

data class PodcastVocabItemUiState(
    val vocab: PodcastVocabEntity,
    val isStudied: Boolean
)

data class PodcastPlayerUiState(
    val episode: PodcastEpisodeEntity? = null,
    val channel: PodcastChannelEntity? = null,
    val sentences: List<PodcastSentenceEntity> = emptyList(),
    val vocabItems: List<PodcastVocabItemUiState> = emptyList(),
    val playerState: AudioPlayerState = AudioPlayerState(),
    val listeningMode: ListeningMode = ListeningMode.BLIND,
    val selectedVocabTab: VocabFilterTab = VocabFilterTab.ALL,
    val showVocabSheet: Boolean = false,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    // Word Detail Sheet
    val selectedWord: Vocabulary? = null,
    val polyReadings: List<Vocabulary> = emptyList(),
    val constituents: List<String> = emptyList(),
    val compounds: List<Vocabulary> = emptyList(),
    val isInStudy: Boolean = false,
    val currentBox: Int? = null,
    val selectedTranslations: Set<String> = emptySet(),
    val isLoading: Boolean = true
)

