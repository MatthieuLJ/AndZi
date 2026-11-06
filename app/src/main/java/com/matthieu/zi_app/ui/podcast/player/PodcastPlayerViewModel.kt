package com.matthieu.zi_app.ui.podcast.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.entity.PodcastEpisodeEntity
import com.matthieu.zi_app.data.local.entity.PodcastVocabEntity
import com.matthieu.zi_app.data.podcast.SlowChineseOffsets
import com.matthieu.zi_app.data.podcast.SlowChineseTranscripts
import com.matthieu.zi_app.data.repository.PodcastRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class PodcastPlayerViewModel(
    private val episodeId: Int,
    private val podcastRepository: PodcastRepository,
    private val localVocabularyRepository: VocabularyRepository,
    private val dictionaryRepository: VocabularyRepository,
    private val studyRepository: StudyRepository,
    val audioPlayer: PodcastAudioPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastPlayerUiState())
    val uiState: StateFlow<PodcastPlayerUiState> = _uiState.asStateFlow()

    init {
        loadEpisodeData()
        observeAudioPlayer()
    }

    private fun loadEpisodeData() {
        viewModelScope.launch {
            var ep = podcastRepository.getEpisodeById(episodeId)
            if (ep == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            if (ep.transcript.isBlank()) {
                val patched = SlowChineseTranscripts.getPatchedTranscript(ep.channelId, ep.episodeNum)
                if (!patched.isNullOrBlank()) {
                    ep = ep.copy(transcript = patched)
                }
            }

            val channel = podcastRepository.getChannelById(ep.channelId)
            val timing = SlowChineseOffsets.getOffset(ep.episodeNum)
            var sentences = podcastRepository.getSentencesForEpisodeSync(episodeId)
            val lastEnd = sentences.lastOrNull()?.endMs ?: 0
            val expectedEnd = if (timing.durationMs > 0) timing.durationMs - timing.outroOffsetMs else 0

            // If sentences are empty or were computed with corrupted/truncated RSS duration, recalculate
            val needsRecalculation = sentences.isEmpty() || (expectedEnd > 0 && lastEnd < (expectedEnd - 15_000))
            if (needsRecalculation && ep.transcript.isNotBlank()) {
                sentences = podcastRepository.extractAndSaveSentencesForEpisode(ep, channel, forceRefresh = true)
            }
            var rawVocab = podcastRepository.getVocabForEpisodeSync(episodeId)

            if (rawVocab.isEmpty() && ep.transcript.isNotBlank()) {
                rawVocab = podcastRepository.extractAndSaveVocabForEpisode(episodeId, ep.transcript)
            }

            val studiedWords = localVocabularyRepository.getAllVocabulary().map { it.chinese }.toSet()
            val vocabItems = rawVocab.map { v ->
                PodcastVocabItemUiState(
                    vocab = v,
                    isStudied = studiedWords.contains(v.chinese)
                )
            }

            val isDownloaded = ep.downloadedPath != null && File(ep.downloadedPath).exists()

            _uiState.update {
                it.copy(
                    episode = ep,
                    channel = channel,
                    sentences = sentences,
                    vocabItems = vocabItems,
                    isDownloaded = isDownloaded,
                    isLoading = false
                )
            }

            val audioSource = podcastRepository.getPlayableAudioSource(ep)
            audioPlayer.loadAudio(
                source = audioSource,
                initialPositionMs = ep.playbackPositionMs,
                autoPlay = false
            )
        }
    }

    private fun observeAudioPlayer() {
        viewModelScope.launch {
            audioPlayer.playerState.collect { pState ->
                _uiState.update { it.copy(playerState = pState) }
            }
        }
    }

    fun togglePlayPause() {
        audioPlayer.togglePlayPause()
    }

    fun pauseAudio() {
        audioPlayer.pause()
    }

    fun seekTo(positionMs: Int) {
        audioPlayer.seekTo(positionMs)
    }

    fun seekBy(offsetMs: Int) {
        audioPlayer.seekBy(offsetMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        audioPlayer.setPlaybackSpeed(speed)
    }

    fun setListeningMode(mode: ListeningMode) {
        _uiState.update { it.copy(listeningMode = mode) }
    }

    fun setVocabFilterTab(tab: VocabFilterTab) {
        _uiState.update { it.copy(selectedVocabTab = tab) }
    }

    fun setShowVocabSheet(show: Boolean) {
        _uiState.update { it.copy(showVocabSheet = show) }
    }

    fun downloadAudio() {
        val ep = _uiState.value.episode ?: return
        if (_uiState.value.isDownloading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f) }
            val res = podcastRepository.downloadEpisodeAudio(ep) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            if (res.isSuccess) {
                _uiState.update { it.copy(isDownloaded = true, isDownloading = false) }
            } else {
                _uiState.update { it.copy(isDownloading = false) }
            }
        }
    }

    fun deleteAudio() {
        viewModelScope.launch {
            podcastRepository.deleteDownloadedAudio(episodeId)
            _uiState.update { it.copy(isDownloaded = false) }
        }
    }

    fun selectWordForDetail(chinese: String) {
        viewModelScope.launch {
            val vocabList = dictionaryRepository.getVocabularyForChinese(chinese)
            val primaryVocab = vocabList.firstOrNull() ?: Vocabulary(
                chinese = chinese,
                pinyin = "",
                english = arrayListOf()
            )

            // Check if word is already tracked in Leitner study
            val leitnerState = studyRepository.getLeitnerState(chinese)

            // Check existing local translations
            val localVocabs = localVocabularyRepository.getVocabularyForChinese(chinese)
            val localMatching = localVocabs.find { it.pinyin == primaryVocab.pinyin } ?: localVocabs.firstOrNull()

            val isInStudy = localMatching != null && localMatching.english.isNotEmpty()
            val selectedTranslations = if (isInStudy) {
                localMatching!!.english.toSet()
            } else {
                emptySet()
            }

            val mergedEnglish = ArrayList((primaryVocab.english + (localMatching?.english ?: emptyList())).distinct())
            val displayWord = Vocabulary(
                chinese = primaryVocab.chinese,
                pinyin = primaryVocab.pinyin.ifEmpty { localMatching?.pinyin ?: "" },
                english = mergedEnglish
            )

            val polyReadings = if (vocabList.size > 1) vocabList.drop(1) else emptyList()
            val constituents = if (chinese.codePointCount(0, chinese.length) > 1) {
                chinese.map { it.toString() }
            } else emptyList()

            val containingCompounds = if (chinese.codePointCount(0, chinese.length) == 1) {
                dictionaryRepository.getWordsContainingCharacter(chinese).take(20)
            } else emptyList()

            _uiState.update {
                it.copy(
                    selectedWord = displayWord,
                    polyReadings = polyReadings,
                    constituents = constituents,
                    compounds = containingCompounds,
                    isInStudy = isInStudy,
                    currentBox = if (isInStudy) (leitnerState?.box ?: 0) else null,
                    selectedTranslations = selectedTranslations
                )
            }
        }
    }

    fun clearWordSelection() {
        _uiState.update {
            it.copy(
                selectedWord = null,
                polyReadings = emptyList(),
                constituents = emptyList(),
                compounds = emptyList(),
                isInStudy = false,
                currentBox = null,
                selectedTranslations = emptySet()
            )
        }
    }

    fun toggleStudyForWord(vocabulary: Vocabulary) {
        viewModelScope.launch {
            val hanzi = vocabulary.chinese
            val currentlyStudied = _uiState.value.isInStudy

            if (!currentlyStudied) {
                val translationsToUse = if (_uiState.value.selectedTranslations.isNotEmpty()) {
                    _uiState.value.selectedTranslations.toList()
                } else {
                    vocabulary.english
                }

                localVocabularyRepository.addVocabulary(
                    Vocabulary(chinese = vocabulary.chinese, pinyin = vocabulary.pinyin, english = ArrayList(translationsToUse))
                )
                studyRepository.recordReviewGrade(hanzi, ReviewGrade.AGAIN)
                val newState = studyRepository.getLeitnerState(hanzi)
                _uiState.update {
                    it.copy(
                        isInStudy = true,
                        currentBox = newState?.box ?: 0,
                        selectedTranslations = translationsToUse.toSet()
                    )
                }
            } else {
                studyRepository.deleteRecord(hanzi)
                localVocabularyRepository.deleteWord(hanzi, vocabulary.pinyin)
                if (hanzi.codePointCount(0, hanzi.length) == 1) {
                    localVocabularyRepository.deleteCharacter(hanzi)
                }
                _uiState.update {
                    it.copy(
                        isInStudy = false,
                        currentBox = null,
                        selectedTranslations = emptySet()
                    )
                }
            }

            // Refresh vocabItems status
            val studiedWords = localVocabularyRepository.getAllVocabulary().map { it.chinese }.toSet()
            _uiState.update { s ->
                s.copy(
                    vocabItems = s.vocabItems.map { vi ->
                        vi.copy(isStudied = studiedWords.contains(vi.vocab.chinese))
                    }
                )
            }
        }
    }

    fun toggleTranslationForStudy(translation: String) {
        val word = _uiState.value.selectedWord ?: return
        viewModelScope.launch {
            val cleanTrans = translation.trim()
            if (cleanTrans.isEmpty()) return@launch

            val currentSelected = _uiState.value.selectedTranslations
            val isSelected = currentSelected.contains(cleanTrans)

            val newSelected = if (isSelected) {
                currentSelected - cleanTrans
            } else {
                currentSelected + cleanTrans
            }

            // Sync with local repository
            if (isSelected) {
                localVocabularyRepository.removeTranslation(word.chinese, word.pinyin, cleanTrans)
            } else {
                localVocabularyRepository.addTranslation(word.chinese, word.pinyin, cleanTrans)
            }

            // Ensure card is in Leitner study queue if selecting
            var isInStudy = _uiState.value.isInStudy
            var box = _uiState.value.currentBox
            if (!isSelected && !isInStudy) {
                studyRepository.recordReviewGrade(word.chinese, ReviewGrade.AGAIN)
                val newState = studyRepository.getLeitnerState(word.chinese)
                isInStudy = true
                box = newState?.box ?: 0
            } else if (isSelected && newSelected.isEmpty()) {
                studyRepository.deleteRecord(word.chinese)
                localVocabularyRepository.deleteWord(word.chinese, word.pinyin)
                if (word.chinese.codePointCount(0, word.chinese.length) == 1) {
                    localVocabularyRepository.deleteCharacter(word.chinese)
                }
                isInStudy = false
                box = null
            }

            _uiState.update {
                it.copy(
                    selectedTranslations = newSelected,
                    isInStudy = isInStudy,
                    currentBox = box
                )
            }

            // Refresh vocabItems status
            val studiedWords = localVocabularyRepository.getAllVocabulary().map { it.chinese }.toSet()
            _uiState.update { s ->
                s.copy(
                    vocabItems = s.vocabItems.map { vi ->
                        vi.copy(isStudied = studiedWords.contains(vi.vocab.chinese))
                    }
                )
            }
        }
    }

    fun addCustomTranslation(customText: String) {
        val word = _uiState.value.selectedWord ?: return
        viewModelScope.launch {
            val cleanTrans = customText.trim()
            if (cleanTrans.isEmpty()) return@launch

            localVocabularyRepository.addTranslation(word.chinese, word.pinyin, cleanTrans)

            var isInStudy = _uiState.value.isInStudy
            var box = _uiState.value.currentBox
            if (!isInStudy) {
                studyRepository.recordReviewGrade(word.chinese, ReviewGrade.AGAIN)
                val newState = studyRepository.getLeitnerState(word.chinese)
                isInStudy = true
                box = newState?.box ?: 0
            }

            val updatedEnglish = ArrayList((word.english + cleanTrans).distinct())
            val updatedWord = Vocabulary(
                chinese = word.chinese,
                pinyin = word.pinyin,
                english = updatedEnglish
            )
            val newSelected = _uiState.value.selectedTranslations + cleanTrans

            _uiState.update {
                it.copy(
                    selectedWord = updatedWord,
                    selectedTranslations = newSelected,
                    isInStudy = isInStudy,
                    currentBox = box
                )
            }

            val studiedWords = localVocabularyRepository.getAllVocabulary().map { it.chinese }.toSet()
            _uiState.update { s ->
                s.copy(
                    vocabItems = s.vocabItems.map { vi ->
                        vi.copy(isStudied = studiedWords.contains(vi.vocab.chinese))
                    }
                )
            }
        }
    }

    fun toggleStudyForVocabItem(vocab: PodcastVocabEntity) {
        viewModelScope.launch {
            val hanzi = vocab.chinese
            val isCurrentlyStudied = _uiState.value.vocabItems.find { it.vocab.chinese == hanzi }?.isStudied == true

            if (!isCurrentlyStudied) {
                val englishList = if (vocab.translation.isNotEmpty()) {
                    arrayListOf(vocab.translation)
                } else {
                    val dictWords = dictionaryRepository.getVocabularyForChinese(hanzi)
                    val match = dictWords.find { it.pinyin == vocab.pinyin } ?: dictWords.firstOrNull()
                    match?.english ?: arrayListOf()
                }
                val vocabObj = Vocabulary(
                    chinese = vocab.chinese,
                    pinyin = vocab.pinyin,
                    english = englishList
                )
                localVocabularyRepository.addVocabulary(vocabObj)
                studyRepository.recordReviewGrade(hanzi, ReviewGrade.AGAIN)
            } else {
                studyRepository.deleteRecord(hanzi)
                localVocabularyRepository.deleteWord(hanzi, vocab.pinyin)
                if (hanzi.codePointCount(0, hanzi.length) == 1) {
                    localVocabularyRepository.deleteCharacter(hanzi)
                }
            }

            val studiedWords = localVocabularyRepository.getAllVocabulary().map { it.chinese }.toSet()
            _uiState.update { s ->
                val updatedVocabItems = s.vocabItems.map { vi ->
                    vi.copy(isStudied = studiedWords.contains(vi.vocab.chinese))
                }
                if (s.selectedWord?.chinese == hanzi) {
                    val willBeStudied = !isCurrentlyStudied
                    s.copy(
                        vocabItems = updatedVocabItems,
                        isInStudy = willBeStudied,
                        currentBox = if (willBeStudied) 0 else null,
                        selectedTranslations = if (willBeStudied) {
                            if (vocab.translation.isNotEmpty()) setOf(vocab.translation) else s.selectedWord.english.toSet()
                        } else emptySet()
                    )
                } else {
                    s.copy(vocabItems = updatedVocabItems)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val currentPos = audioPlayer.playerState.value.currentPositionMs
        val duration = audioPlayer.playerState.value.durationMs
        val isCompleted = duration > 0 && currentPos >= (duration - 3000)

        viewModelScope.launch {
            podcastRepository.updatePlaybackPosition(episodeId, currentPos, isCompleted)
        }
        audioPlayer.release()
    }

    companion object {
        fun provideFactory(
            episodeId: Int,
            podcastRepository: PodcastRepository,
            localVocabularyRepository: VocabularyRepository,
            dictionaryRepository: VocabularyRepository,
            studyRepository: StudyRepository,
            audioPlayer: PodcastAudioPlayer
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastPlayerViewModel(
                    episodeId,
                    podcastRepository,
                    localVocabularyRepository,
                    dictionaryRepository,
                    studyRepository,
                    audioPlayer
                ) as T
            }
        }
    }
}
