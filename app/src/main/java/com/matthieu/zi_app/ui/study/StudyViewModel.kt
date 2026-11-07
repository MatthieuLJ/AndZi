package com.matthieu.zi_app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.LeitnerState
import com.matthieu.zi_app.Pinyin
import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.domain.study.AdaptiveQuizPolicy
import com.matthieu.zi_app.domain.study.quiz.QuizAction
import com.matthieu.zi_app.domain.study.quiz.QuizBuildContext
import com.matthieu.zi_app.domain.study.quiz.QuizDistractorGenerator
import com.matthieu.zi_app.domain.study.quiz.QuizEligibilityContext
import com.matthieu.zi_app.domain.study.quiz.QuizSessionState
import com.matthieu.zi_app.domain.study.quiz.QuizStrategyRegistry
import com.matthieu.zi_app.domain.writing.HanziPoint
import com.matthieu.zi_app.domain.writing.StrokeMatcher
import com.matthieu.zi_app.domain.writing.WritingScaffoldingLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StudyViewModel(
    private val studyRepository: StudyRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val dictionaryRepository: VocabularyRepository? = null,
    private val userPreferences: UserPreferences? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState(isLoading = true))
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private var testStepIndex = 0
    private var lastTestedHanzi: String? = null
    private var lastTestedWord: String? = null
    private var lastPreReviewState: LeitnerState? = null
    private val recentTestedWords = ArrayDeque<String>()

    init {
        startStudySession()
    }

    fun startStudySession() {
        viewModelScope.launch {
            testStepIndex = 0
            lastTestedHanzi = null
            lastTestedWord = null
            lastPreReviewState = null
            recentTestedWords.clear()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    sessionStats = SessionStats(),
                    isSessionFinished = false,
                    selectedLeftMatchIndex = null,
                    selectedRightMatchIndex = null,
                    matchedWords = emptySet(),
                    failedMatchWords = emptySet(),
                    matchErrorShakeTrigger = 0
                )
            }
            loadNextCard(isInitialLoad = true)
        }
    }

    private suspend fun loadNextCard(isInitialLoad: Boolean) {
        try {
            val now = System.currentTimeMillis()
            val dueCandidates = studyRepository.getStudyQueue(limit = 20, now = now)

            val nextHanzi: String? = when {
                dueCandidates.isNotEmpty() -> {
                    // Priority 0: Not similar to last tested word AND not similar to recent words in session
                    val nonSimilarToRecent = dueCandidates.firstOrNull { candidate ->
                        candidate != lastTestedHanzi &&
                        candidate != lastTestedWord &&
                        !areWordsSimilar(candidate, lastTestedHanzi) &&
                        !areWordsSimilar(candidate, lastTestedWord) &&
                        recentTestedWords.none { areWordsSimilar(candidate, it) }
                    }

                    // Priority 1: Not similar to immediately preceding word (consecutive tests do not share any character)
                    val nonSimilarToLast = dueCandidates.firstOrNull { candidate ->
                        candidate != lastTestedHanzi &&
                        candidate != lastTestedWord &&
                        !areWordsSimilar(candidate, lastTestedHanzi) &&
                        !areWordsSimilar(candidate, lastTestedWord)
                    }

                    // Priority 2: Different word from last tested word
                    val differentFromLast = dueCandidates.firstOrNull { candidate ->
                        candidate != lastTestedHanzi && candidate != lastTestedWord
                    }

                    // Fallback: First candidate in queue
                    nonSimilarToRecent ?: nonSimilarToLast ?: differentFromLast ?: dueCandidates.first()
                }
                isInitialLoad -> {
                    val allVoc = vocabularyRepository.getAllVocabulary()
                    if (allVoc.isNotEmpty()) {
                        allVoc.first().chinese
                    } else {
                        val distinctUser = vocabularyRepository.getAllDistinctHanzi()
                        if (distinctUser.isNotEmpty()) {
                            distinctUser.first()
                        } else {
                            dictionaryRepository?.getAllDistinctHanzi()?.firstOrNull()
                        }
                    }
                }
                else -> null
            }

            if (nextHanzi == null) {
                _uiState.update {
                    it.copy(
                        queue = emptyList(),
                        currentIndex = 0,
                        remainingDueCount = 0,
                        isAnswerRevealed = false,
                        isCorrect = null,
                        selectedOption = null,
                        selectedPinyinTone = null,
                        assembledChars = emptyList(),
                        usedTileIndices = emptyList(),
                        completedStrokes = emptyList(),
                        currentStrokeIndex = 0,
                        scaffoldingLevel = WritingScaffoldingLevel.TEST,
                        hasUsedWritingGuide = false,
                        writingErrorShakeTrigger = 0,
                        isContextWordsRevealed = false,
                        selectedLeftMatchIndex = null,
                        selectedRightMatchIndex = null,
                        matchedWords = emptySet(),
                        failedMatchWords = emptySet(),
                        matchErrorShakeTrigger = 0,
                        isSessionFinished = true,
                        isLoading = false
                    )
                }
                return
            }

            val card = buildStudyCard(nextHanzi, testStepIndex)
            testStepIndex++
            lastTestedHanzi = nextHanzi
            val revealedWord = card.revealedHanzi
            lastTestedWord = revealedWord
            recentTestedWords.addLast(revealedWord)
            if (nextHanzi != revealedWord) {
                recentTestedWords.addLast(nextHanzi)
            }
            while (recentTestedWords.size > 8) {
                recentTestedWords.removeFirst()
            }
            val dueCount = studyRepository.getNumExpired(now)

            _uiState.update {
                it.copy(
                    queue = listOf(card),
                    currentIndex = 0,
                    remainingDueCount = dueCount,
                    isAnswerRevealed = false,
                    isCorrect = null,
                    selectedOption = null,
                    selectedPinyinTone = null,
                    assembledChars = emptyList(),
                    usedTileIndices = emptyList(),
                    completedStrokes = emptyList(),
                    currentStrokeIndex = 0,
                    scaffoldingLevel = WritingScaffoldingLevel.TEST,
                    hasUsedWritingGuide = false,
                    writingErrorShakeTrigger = 0,
                    isContextWordsRevealed = false,
                    selectedLeftMatchIndex = null,
                    selectedRightMatchIndex = null,
                    matchedWords = emptySet(),
                    failedMatchWords = emptySet(),
                    matchErrorShakeTrigger = 0,
                    isSessionFinished = false,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to load study card"
                )
            }
        }
    }

    private suspend fun buildStudyCard(hanzi: String, index: Int): StudyCard {
        val studyChars = (
            vocabularyRepository.getAllDistinctHanzi() +
            run {
                var idx = 0
                val chars = mutableListOf<String>()
                while (idx < hanzi.length) {
                    val cp = hanzi.codePointAt(idx)
                    chars.add(String(Character.toChars(cp)))
                    idx += Character.charCount(cp)
                }
                chars
            }
        ).distinct().toSet()

        val userEnglish = vocabularyRepository.getAllVocabulary()
            .flatMap { it.english }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val dictEnglish = if (userEnglish.size < 20 && dictionaryRepository != null) {
            dictionaryRepository.getRandomTranslations(limit = 40)
        } else emptyList()

        val allStudyEnglish = (userEnglish + dictEnglish).distinct().toSet()

        val localVocList = vocabularyRepository.getVocabularyForChinese(hanzi)
        val localVoc = localVocList.firstOrNull { it.english.isNotEmpty() } ?: localVocList.firstOrNull()

        val hasUserEnglish = localVoc != null && localVoc.english.isNotEmpty()
        val voc = if (localVoc != null) {
            localVoc
        } else {
            val localChars = vocabularyRepository.getCharPinyinsForHanzi(hanzi)
            val dictChars = if (localChars.isEmpty() && dictionaryRepository != null) {
                dictionaryRepository.getCharPinyinsForHanzi(hanzi)
            } else emptyList()
            val pinyin = (localChars.firstOrNull() ?: dictChars.firstOrNull())?.pinyin ?: ""
            Vocabulary(chinese = hanzi, pinyin = pinyin, english = ArrayList())
        }

        val leitnerState = studyRepository.getLeitnerState(hanzi)
        val box = leitnerState?.box ?: 0

        val pinyins = if (voc.pinyin.isNotBlank()) {
            PinyinParser.parse_string_to_pinyin(voc.pinyin) ?: emptyList()
        } else emptyList()
        val firstPinyin = pinyins.firstOrNull()
        val tone = firstPinyin?.tone?.takeIf { it in 1..4 } ?: 0
        val syllable = firstPinyin?.radical ?: (if (voc.pinyin.isNotBlank()) voc.pinyin else hanzi)

        val charCount = hanzi.codePointCount(0, hanzi.length)
        val isSingleChar = (charCount == 1)
        val isTwoChar = (charCount == 2)
        val hasValidNonNeutralTone = isSingleChar && (firstPinyin != null && tone in 1..4)

        val matchCompanions = if (isTwoChar && box >= 1) {
            val targetPairCount = when {
                box >= 6 -> 5
                box >= 4 -> 4
                else -> 3
            }
            val allUserVoc = vocabularyRepository.getAllVocabulary()
            val now = System.currentTimeMillis()
            val dueQueue = studyRepository.getStudyQueue(limit = 200, now = now).toSet()

            // Prioritize 2-character words that need to be studied (in study queue / expired / box 0)
            val (dueCandidates, nonDueCandidates) = allUserVoc
                .filter { it.chinese != hanzi && it.chinese.codePointCount(0, it.chinese.length) == 2 }
                .partition { it.chinese in dueQueue }

            val prioritizedCandidates = dueCandidates.shuffled() + nonDueCandidates.shuffled()

            QuizDistractorGenerator.findMatchCompanions(
                primaryWord = hanzi,
                targetPairCount = targetPairCount,
                minPairCount = 3,
                candidates = prioritizedCandidates,
                vocabularyRepository = vocabularyRepository,
                dictionaryRepository = dictionaryRepository
            )
        } else emptyList()
        val canPairMatch = isTwoChar && box >= 1 && matchCompanions.size >= 2

        val containingWords = if (isSingleChar) {
            vocabularyRepository.getWordsContainingCharacter(hanzi).filter { it.chinese.length > 1 }
        } else emptyList()

        val writingEnabled = userPreferences?.writingQuizEnabled?.value ?: false
        val charStrokes = if (isSingleChar && writingEnabled) {
            vocabularyRepository.getHanziCharacter(hanzi)
                ?: dictionaryRepository?.getHanziCharacter(hanzi)
        } else null

        val eligibilityContext = QuizEligibilityContext(
            box = box,
            isSingleChar = isSingleChar,
            hasValidNonNeutralTone = hasValidNonNeutralTone,
            hasUserEnglish = hasUserEnglish,
            hasContainingWords = containingWords.isNotEmpty(),
            strokeCount = charStrokes?.strokeCount,
            canPairMatch = canPairMatch
        )

        // 1. Determine Quiz Type using AdaptiveQuizPolicy:
        val quizType = AdaptiveQuizPolicy.selectQuizType(eligibilityContext, index)

        // 2. Build payload using the corresponding QuizStrategy:
        val buildContext = QuizBuildContext(
            box = box,
            index = index,
            studyChars = studyChars,
            containingWords = containingWords,
            allStudyEnglish = allStudyEnglish,
            strokeData = charStrokes,
            matchCompanions = matchCompanions,
            vocabularyRepository = vocabularyRepository,
            dictionaryRepository = dictionaryRepository,
            studyRepository = studyRepository
        )

        val strategy = QuizStrategyRegistry.getStrategy(quizType)
        val payload = strategy.buildPayload(voc, buildContext)

        return StudyCard.fromPayload(
            hanzi = hanzi,
            pinyin = voc.pinyin,
            syllable = syllable,
            tone = tone,
            english = voc.english,
            currentBox = box,
            quizType = quizType,
            payload = payload
        )
    }

    fun onQuizAction(action: QuizAction) {
        when (action) {
            is QuizAction.ToneSelected -> submitToneAnswer(action.tone)
            is QuizAction.EnglishSelected -> submitEnglishAnswer(action.option)
            is QuizAction.ClozeSelected -> submitClozeAnswer(action.character)
            is QuizAction.PinyinSyllableSelected -> selectPinyinSyllable(action.syllable)
            is QuizAction.PinyinToneSelected -> selectPinyinTone(action.tone)
            is QuizAction.AssemblyTileSelected -> selectAssemblyTile(action.index, action.character)
            is QuizAction.UndoAssemblyTile -> undoAssemblyTile()
            is QuizAction.MatchLeftSelected -> selectMatchLeftTile(action.index)
            is QuizAction.MatchRightSelected -> selectMatchRightTile(action.index)
            is QuizAction.WritingStrokeDrawn -> onUserStrokeDrawn(action.points)
            is QuizAction.SetWritingScaffolding -> setWritingScaffoldingLevel(action.level)
            is QuizAction.UndoWritingStroke -> undoWritingStroke()
            is QuizAction.ClearWritingCanvas -> clearWritingCanvas()
            is QuizAction.SurrenderWriting -> surrenderWriting()
            is QuizAction.RevealContextWords -> revealContextWords()
        }
    }

    fun submitToneAnswer(selectedTone: Int) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val isCorrect = (selectedTone == card.tone)
        processAnswer(card, isCorrect, selectedTone.toString())
    }

    fun selectPinyinSyllable(syllable: String) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val hasTone = card.tone in 1..4
        if (!hasTone) {
            val isCorrect = syllable.equals(card.syllable, ignoreCase = true)
            _uiState.update { it.copy(selectedOption = syllable) }
            processAnswer(card, isCorrect, syllable)
            return
        }

        val currentTone = _uiState.value.selectedPinyinTone
        if (currentTone != null) {
            _uiState.update { it.copy(selectedOption = syllable) }
            val isSyllableCorrect = syllable.equals(card.syllable, ignoreCase = true)
            val isToneCorrect = currentTone == card.tone
            val isCorrect = isSyllableCorrect && isToneCorrect
            val combined = PinyinParser.convert_to_pretty_version("$syllable$currentTone")
            val gradeOverride = if (isSyllableCorrect && !isToneCorrect) ReviewGrade.HARD else null
            processAnswer(card, isCorrect, combined, gradeOverride)
        } else {
            _uiState.update { it.copy(selectedOption = syllable) }
        }
    }

    fun selectPinyinTone(tone: Int) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val currentSyllable = _uiState.value.selectedOption
        if (currentSyllable != null) {
            _uiState.update { it.copy(selectedPinyinTone = tone) }
            val isSyllableCorrect = currentSyllable.equals(card.syllable, ignoreCase = true)
            val isToneCorrect = tone == card.tone
            val isCorrect = isSyllableCorrect && isToneCorrect
            val combined = PinyinParser.convert_to_pretty_version("$currentSyllable$tone")
            val gradeOverride = if (isSyllableCorrect && !isToneCorrect) ReviewGrade.HARD else null
            processAnswer(card, isCorrect, combined, gradeOverride)
        } else {
            _uiState.update { it.copy(selectedPinyinTone = tone) }
        }
    }

    fun submitPinyinAnswer(selectedPinyin: String) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val p = PinyinParser.parse_string_to_pinyin(selectedPinyin)?.firstOrNull()
        val parsedSyllable = p?.radical ?: selectedPinyin.filter { !it.isDigit() }
        val parsedTone = if (p != null && p.tone in 1..4) p.tone else null

        val hasTone = card.tone in 1..4
        val isSyllableCorrect = parsedSyllable.equals(card.syllable, ignoreCase = true)
        val isToneCorrect = if (hasTone) parsedTone == card.tone else true
        val isCorrect = isSyllableCorrect && isToneCorrect

        val gradeOverride = if (isSyllableCorrect && !isToneCorrect) ReviewGrade.HARD else null

        _uiState.update {
            it.copy(
                selectedOption = parsedSyllable,
                selectedPinyinTone = parsedTone
            )
        }
        processAnswer(card, isCorrect, selectedPinyin, gradeOverride)
    }

    fun submitPinyinAnswer(syllable: String, tone: Int?) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val hasTone = card.tone in 1..4
        val isCorrect = if (hasTone) {
            syllable.equals(card.syllable, ignoreCase = true) && tone == card.tone
        } else {
            syllable.equals(card.syllable, ignoreCase = true)
        }

        val display = if (tone != null && tone in 1..4) {
            PinyinParser.convert_to_pretty_version("$syllable$tone")
        } else {
            syllable
        }

        _uiState.update {
            it.copy(
                selectedOption = syllable,
                selectedPinyinTone = tone
            )
        }
        processAnswer(card, isCorrect, display)
    }

    fun submitEnglishAnswer(selectedMeaning: String) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val isCorrect = card.english.any { it.equals(selectedMeaning, ignoreCase = true) }
        processAnswer(card, isCorrect, selectedMeaning)
    }

    fun submitClozeAnswer(selectedChar: String) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val targetChar = if (card.clozeTargetIndex in card.wordSlots.indices) {
            card.wordSlots[card.clozeTargetIndex]
        } else ""

        val isCorrect = (selectedChar == targetChar)
        processAnswer(card, isCorrect, selectedChar)
    }

    fun selectAssemblyTile(tileIndex: Int, tileChar: String) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return
        val currentUsed = _uiState.value.usedTileIndices

        val charToAssemble = card.scrambledTiles.getOrNull(tileIndex) ?: tileChar
        val maxAllowed = maxOf(1, card.wordSlots.count { it == charToAssemble })
        val timesUsed = currentUsed.count { it == tileIndex }
        if (timesUsed >= maxAllowed) return
        if (_uiState.value.assembledChars.size >= card.wordSlots.size) return

        val updatedAssembled = _uiState.value.assembledChars + charToAssemble
        val updatedUsed = currentUsed + tileIndex

        if (updatedAssembled.size >= card.wordSlots.size) {
            val assembledWord = updatedAssembled.joinToString("")
            val isCorrect = (assembledWord == card.hanzi)
            _uiState.update { it.copy(assembledChars = updatedAssembled, usedTileIndices = updatedUsed) }
            processAnswer(card, isCorrect, assembledWord)
        } else {
            _uiState.update { it.copy(assembledChars = updatedAssembled, usedTileIndices = updatedUsed) }
        }
    }

    fun undoAssemblyTile() {
        if (_uiState.value.isAnswerRevealed) return
        val currentAssembled = _uiState.value.assembledChars
        val currentUsed = _uiState.value.usedTileIndices
        if (currentAssembled.isNotEmpty() && currentUsed.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    assembledChars = currentAssembled.dropLast(1),
                    usedTileIndices = currentUsed.dropLast(1)
                )
            }
        }
    }

    fun selectMatchLeftTile(index: Int) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return
        val tile = card.leftMatchTiles.getOrNull(index) ?: return
        if (_uiState.value.matchedWords.any { it.startsWith(tile) }) return

        _uiState.update { it.copy(selectedLeftMatchIndex = index) }
        val rightIdx = _uiState.value.selectedRightMatchIndex
        if (rightIdx != null) {
            evaluateMatchPair(index, rightIdx)
        }
    }

    fun selectMatchRightTile(index: Int) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return
        val tile = card.rightMatchTiles.getOrNull(index) ?: return
        if (_uiState.value.matchedWords.any { it.endsWith(tile) }) return

        _uiState.update { it.copy(selectedRightMatchIndex = index) }
        val leftIdx = _uiState.value.selectedLeftMatchIndex
        if (leftIdx != null) {
            evaluateMatchPair(leftIdx, index)
        }
    }

    private fun evaluateMatchPair(leftIndex: Int, rightIndex: Int) {
        val card = _uiState.value.currentCard ?: return
        val leftChar = card.leftMatchTiles.getOrNull(leftIndex) ?: return
        val rightChar = card.rightMatchTiles.getOrNull(rightIndex) ?: return
        val formedWord = leftChar + rightChar

        val matchedPair = card.matchPairs.find { it.word == formedWord }
        if (matchedPair != null) {
            val updatedMatched = _uiState.value.matchedWords + formedWord
            val allMatched = updatedMatched.size >= card.matchPairs.size

            if (allMatched) {
                _uiState.update {
                    it.copy(
                        matchedWords = updatedMatched,
                        selectedLeftMatchIndex = null,
                        selectedRightMatchIndex = null
                    )
                }
                processPairMatchCompletion(card, updatedMatched)
            } else {
                _uiState.update {
                    it.copy(
                        matchedWords = updatedMatched,
                        selectedLeftMatchIndex = null,
                        selectedRightMatchIndex = null
                    )
                }
            }
        } else {
            val involvedWords = card.matchPairs.filter { it.firstChar == leftChar || it.secondChar == rightChar }.map { it.word }.toSet()
            _uiState.update {
                it.copy(
                    failedMatchWords = it.failedMatchWords + involvedWords,
                    matchErrorShakeTrigger = it.matchErrorShakeTrigger + 1,
                    selectedLeftMatchIndex = null,
                    selectedRightMatchIndex = null
                )
            }
        }
    }

    private fun processPairMatchCompletion(card: StudyCard, matchedWords: Set<String>) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            lastPreReviewState = studyRepository.getLeitnerState(card.hanzi)
            val isPrimaryFailed = _uiState.value.failedMatchWords.contains(card.hanzi)

            // 1. Primary word: active test -> update SRS score (GOOD or AGAIN)
            val primaryGrade = if (!isPrimaryFailed) ReviewGrade.GOOD else ReviewGrade.AGAIN
            studyRepository.recordReviewGrade(card.hanzi, primaryGrade, now)

            // 2. Companion words:
            for (pair in card.matchPairs.filter { it.word != card.hanzi }) {
                val isFailed = _uiState.value.failedMatchWords.contains(pair.word)
                if (isFailed) {
                    studyRepository.recordReviewGrade(pair.word, ReviewGrade.AGAIN, now)
                } else {
                    val state = studyRepository.getLeitnerState(pair.word)
                    if (state != null && (state.expTime <= now || state.box == 0)) {
                        // Due companion word matched cleanly -> promote
                        studyRepository.recordReviewGrade(pair.word, ReviewGrade.GOOD, now)
                    } else {
                        // Non-due companion word matched cleanly -> refresh timer without updating box
                        studyRepository.refreshCardTimer(pair.word, now)
                    }
                }
            }

            // 3. Characters displayed & used:
            // "for the other characters displayed, we can reset the timer without updating the box"
            val allChars = card.matchPairs.flatMap { listOf(it.firstChar to it.word, it.secondChar to it.word) }
            for ((char, word) in allChars) {
                val isFailed = _uiState.value.failedMatchWords.contains(word)
                if (isFailed) {
                    studyRepository.recordReviewGrade(char, ReviewGrade.AGAIN, now)
                } else {
                    // Cleanly matched character -> refresh timer without updating box
                    studyRepository.refreshCardTimer(char, now)
                }
            }

            userPreferences?.recordStudySession(now)
            val dueCount = studyRepository.getNumExpired(now)

            _uiState.update { state ->
                val prevStats = state.sessionStats
                val isSuccess = !isPrimaryFailed
                val newStats = prevStats.copy(
                    totalReviewed = prevStats.totalReviewed + 1,
                    correctCount = prevStats.correctCount + if (isSuccess) 1 else 0,
                    lapseCount = prevStats.lapseCount + if (!isSuccess) 1 else 0
                )
                state.copy(
                    remainingDueCount = dueCount,
                    isAnswerRevealed = true,
                    isCorrect = isSuccess,
                    selectedOption = card.hanzi,
                    sessionStats = newStats
                )
            }
        }
    }

    fun onUserStrokeDrawn(points: List<HanziPoint>) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return
        val charData = card.writingCharacter ?: return
        val currentIdx = _uiState.value.currentStrokeIndex
        if (currentIdx !in charData.strokes.indices) return

        val isOutlineVisible = _uiState.value.scaffoldingLevel == WritingScaffoldingLevel.GUIDED_FULL
        val matchResult = StrokeMatcher.strokeMatches(
            userPointsRaw = points,
            character = charData,
            strokeNum = currentIdx,
            isOutlineVisible = isOutlineVisible
        )

        if (matchResult.isMatch) {
            val updatedCompleted = _uiState.value.completedStrokes + charData.strokes[currentIdx]
            val nextIdx = currentIdx + 1
            if (nextIdx >= charData.strokes.size) {
                _uiState.update {
                    it.copy(
                        completedStrokes = updatedCompleted,
                        currentStrokeIndex = nextIdx
                    )
                }
                if (_uiState.value.hasUsedWritingGuide || _uiState.value.scaffoldingLevel != WritingScaffoldingLevel.TEST) {
                    processGuidedWritingAnswer(card, charData.strokeCount)
                } else {
                    processAnswer(card, isCorrect = true, selectedInput = card.hanzi)
                }
            } else {
                _uiState.update {
                    it.copy(
                        completedStrokes = updatedCompleted,
                        currentStrokeIndex = nextIdx
                    )
                }
            }
        } else {
            _uiState.update { it.copy(writingErrorShakeTrigger = it.writingErrorShakeTrigger + 1) }
        }
    }

    fun setWritingScaffoldingLevel(level: WritingScaffoldingLevel) {
        if (_uiState.value.isAnswerRevealed) return
        _uiState.update {
            it.copy(
                scaffoldingLevel = level,
                hasUsedWritingGuide = it.hasUsedWritingGuide || (level != WritingScaffoldingLevel.TEST)
            )
        }
    }

    fun undoWritingStroke() {
        if (_uiState.value.isAnswerRevealed) return
        val completed = _uiState.value.completedStrokes
        if (completed.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    completedStrokes = completed.dropLast(1),
                    currentStrokeIndex = (it.currentStrokeIndex - 1).coerceAtLeast(0)
                )
            }
        }
    }

    fun clearWritingCanvas() {
        if (_uiState.value.isAnswerRevealed) return
        _uiState.update {
            it.copy(
                completedStrokes = emptyList(),
                currentStrokeIndex = 0
            )
        }
    }

    fun surrenderWriting() {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return
        processAnswer(card, isCorrect = false, selectedInput = "Given up")
    }

    private fun processGuidedWritingAnswer(card: StudyCard, strokeCount: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            lastPreReviewState = studyRepository.getLeitnerState(card.hanzi)
            studyRepository.recordGuidedWritingResult(card.hanzi, strokeCount, now)

            if (card.wordSlots.isNotEmpty()) {
                val contextWord = card.wordSlots.joinToString("")
                studyRepository.refreshCardTimer(contextWord, now)
                val companionChars = card.wordSlots.filter { it != card.hanzi }
                studyRepository.refreshCardTimers(companionChars, now)
            }

            userPreferences?.recordStudySession(now)
            val dueCount = studyRepository.getNumExpired(now)

            _uiState.update { state ->
                val prevStats = state.sessionStats
                val newStats = prevStats.copy(
                    totalReviewed = prevStats.totalReviewed + 1,
                    correctCount = prevStats.correctCount + 1
                )
                state.copy(
                    remainingDueCount = dueCount,
                    isAnswerRevealed = true,
                    isCorrect = true,
                    selectedOption = "Guided Practice",
                    sessionStats = newStats
                )
            }
        }
    }

    fun revealContextWords() {
        if (_uiState.value.isAnswerRevealed) return
        _uiState.update { it.copy(isContextWordsRevealed = true) }
    }

    private fun processAnswer(
        card: StudyCard,
        isCorrect: Boolean,
        selectedInput: String,
        gradeOverride: ReviewGrade? = null
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            lastPreReviewState = studyRepository.getLeitnerState(card.hanzi)

            val isBonusToneOrPinyin = isCorrect &&
                (card.quizType == QuizType.TONE || card.quizType == QuizType.PINYIN) &&
                card.contextWords.isNotEmpty() &&
                !_uiState.value.isContextWordsRevealed

            val grade = when {
                gradeOverride != null -> gradeOverride
                !isCorrect -> ReviewGrade.AGAIN
                isBonusToneOrPinyin -> ReviewGrade.EASY
                else -> ReviewGrade.GOOD
            }
            studyRepository.recordReviewGrade(card.hanzi, grade, now)

            // Reset timers without updating box for auxiliary characters/words displayed in test
            if (isCorrect || grade == ReviewGrade.HARD) {
                when (card.quizType) {
                    QuizType.WORD_ASSEMBLY -> {
                        // Refresh constituent characters without updating their box
                        val constituentChars = card.wordSlots.filter { it != card.hanzi }
                        studyRepository.refreshCardTimers(constituentChars, now)
                    }
                    QuizType.CLOZE -> {
                        // Refresh visible (non-target) constituent characters without updating their box
                        val visibleChars = card.wordSlots.filterIndexed { index, char ->
                            index != card.clozeTargetIndex && char != card.hanzi
                        }
                        studyRepository.refreshCardTimers(visibleChars, now)
                        val missingChar = card.wordSlots.getOrNull(card.clozeTargetIndex)
                        if (missingChar != null && missingChar != card.hanzi) {
                            studyRepository.refreshCardTimer(missingChar, now)
                        }
                    }
                    QuizType.WRITING -> {
                        if (card.wordSlots.isNotEmpty()) {
                            val contextWord = card.wordSlots.joinToString("")
                            studyRepository.refreshCardTimer(contextWord, now)
                            val companionChars = card.wordSlots.filter { it != card.hanzi }
                            studyRepository.refreshCardTimers(companionChars, now)
                        }
                    }
                    QuizType.TONE, QuizType.PINYIN -> {
                        if (_uiState.value.isContextWordsRevealed && card.contextWords.isNotEmpty()) {
                            studyRepository.refreshCardTimers(card.contextWords, now)
                        }
                    }
                    else -> {}
                }
            }

            userPreferences?.recordStudySession(now)
            val dueCount = studyRepository.getNumExpired(now)

            _uiState.update { state ->
                val prevStats = state.sessionStats
                val isSuccess = isCorrect || grade == ReviewGrade.HARD
                val newStats = prevStats.copy(
                    totalReviewed = prevStats.totalReviewed + 1,
                    correctCount = prevStats.correctCount + if (isSuccess) 1 else 0,
                    lapseCount = prevStats.lapseCount + if (grade == ReviewGrade.AGAIN) 1 else 0
                )
                state.copy(
                    remainingDueCount = dueCount,
                    isAnswerRevealed = true,
                    isCorrect = isCorrect,
                    selectedOption = selectedInput,
                    sessionStats = newStats
                )
            }
        }
    }

    fun markAnswerAsAcceptable() {
        val state = _uiState.value
        val card = state.currentCard ?: return
        if (!state.isAnswerRevealed || state.isCorrect != false) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            studyRepository.overrideReviewGrade(card.hanzi, lastPreReviewState, ReviewGrade.GOOD, now)
            userPreferences?.recordStudySession(now)
            val dueCount = studyRepository.getNumExpired(now)

            _uiState.update { current ->
                val prevStats = current.sessionStats
                val newStats = prevStats.copy(
                    correctCount = prevStats.correctCount + 1,
                    lapseCount = (prevStats.lapseCount - 1).coerceAtLeast(0)
                )
                current.copy(
                    remainingDueCount = dueCount,
                    isCorrect = true,
                    sessionStats = newStats
                )
            }
        }
    }

    fun rateCard(grade: ReviewGrade) {
        val card = _uiState.value.currentCard ?: return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            studyRepository.recordReviewGrade(card.hanzi, grade, now)
            userPreferences?.recordStudySession(now)
            loadNextCard(isInitialLoad = false)
        }
    }

    fun nextCard() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            loadNextCard(isInitialLoad = false)
        }
    }

    companion object {
        fun extractCharacters(word: String): Set<String> {
            val chars = mutableSetOf<String>()
            var idx = 0
            while (idx < word.length) {
                val cp = word.codePointAt(idx)
                chars.add(String(Character.toChars(cp)))
                idx += Character.charCount(cp)
            }
            return chars
        }

        fun areWordsSimilar(wordA: String?, wordB: String?): Boolean {
            if (wordA.isNullOrEmpty() || wordB.isNullOrEmpty()) return false
            val charsA = extractCharacters(wordA)
            val charsB = extractCharacters(wordB)
            return charsA.any { it in charsB }
        }

        val DEFAULT_FALLBACK_ENGLISH_DISTRACTORS = listOf(
            "to see; to look",
            "water",
            "person; people",
            "big; large",
            "small; tiny",
            "to eat",
            "to go",
            "good; well",
            "friend",
            "book; letter",
            "sun; day",
            "moon; month",
            "mountain",
            "heart; mind",
            "tree; wood",
            "fire",
            "to speak; to say",
            "time; hour",
            "sky; heaven",
            "home; family"
        )

        val DEFAULT_FALLBACK_PINYIN_DISTRACTORS = listOf(
            "hao3", "ni3", "da4", "xiao3", "shui3", "ren2", "tian1", "di4",
            "zhong1", "guo2", "shang4", "xia4", "yue4", "ri4", "shan1", "mu4"
        )

        fun provideFactory(
            studyRepository: StudyRepository,
            vocabularyRepository: VocabularyRepository,
            dictionaryRepository: VocabularyRepository? = null,
            userPreferences: UserPreferences? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StudyViewModel(studyRepository, vocabularyRepository, dictionaryRepository, userPreferences) as T
            }
        }
    }
}
