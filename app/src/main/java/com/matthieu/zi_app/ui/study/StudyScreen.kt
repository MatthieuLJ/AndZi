package com.matthieu.zi_app.ui.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.R
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.domain.writing.HanziPoint
import com.matthieu.zi_app.domain.writing.WritingScaffoldingLevel
import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.ui.study.components.ClozeAssemblyView
import com.matthieu.zi_app.ui.study.components.EnglishMcqView
import com.matthieu.zi_app.ui.study.components.PinyinQuizView
import com.matthieu.zi_app.ui.study.components.RevealedFeedbackCard
import com.matthieu.zi_app.ui.study.components.StudyFinishedView
import com.matthieu.zi_app.ui.study.components.StudyWordsUsingCharacterSection
import com.matthieu.zi_app.ui.study.components.ToneKeypad
import com.matthieu.zi_app.ui.study.components.WritingQuizView
import com.matthieu.zi_app.ui.study.components.detectToneGestures
import com.matthieu.zi_app.ui.theme.AndZiThemeCustom

@Composable
fun StudyScreen(
    viewModel: StudyViewModel,
    onNavigateBack: () -> Unit,
    onReturnHome: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startStudySession()
    }

    StudyScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onReturnHome = onReturnHome,
        onToneSelected = { viewModel.submitToneAnswer(it) },
        onEnglishSelected = { viewModel.submitEnglishAnswer(it) },
        onClozeSelected = { viewModel.submitClozeAnswer(it) },
        onPinyinSelected = { viewModel.submitPinyinAnswer(it) },
        onAssemblyTileSelected = { index, char -> viewModel.selectAssemblyTile(index, char) },
        onUndoAssemblyTile = { viewModel.undoAssemblyTile() },
        onWritingStrokeDrawn = { viewModel.onUserStrokeDrawn(it) },
        onSetWritingScaffolding = { viewModel.setWritingScaffoldingLevel(it) },
        onUndoWritingStroke = { viewModel.undoWritingStroke() },
        onClearWritingCanvas = { viewModel.clearWritingCanvas() },
        onSurrenderWriting = { viewModel.surrenderWriting() },
        onAcceptAnswer = { viewModel.markAnswerAsAcceptable() },
        onRateGrade = { viewModel.rateCard(it) },
        onRevealContextWords = { viewModel.revealContextWords() },
        onNextCard = { viewModel.nextCard() },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreenContent(
    uiState: StudyUiState,
    onNavigateBack: () -> Unit,
    onToneSelected: (Int) -> Unit,
    onEnglishSelected: (String) -> Unit,
    onClozeSelected: (String) -> Unit,
    onPinyinSelected: (String) -> Unit = {},
    onAssemblyTileSelected: (Int, String) -> Unit = { _, _ -> },
    onUndoAssemblyTile: () -> Unit = {},
    onWritingStrokeDrawn: (List<HanziPoint>) -> Unit = {},
    onSetWritingScaffolding: (WritingScaffoldingLevel) -> Unit = {},
    onUndoWritingStroke: () -> Unit = {},
    onClearWritingCanvas: () -> Unit = {},
    onSurrenderWriting: () -> Unit = {},
    onAcceptAnswer: () -> Unit = {},
    onRateGrade: (ReviewGrade) -> Unit = {},
    onRevealContextWords: () -> Unit = {},
    onNextCard: () -> Unit,
    onReturnHome: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!uiState.isSessionFinished && uiState.currentCard != null) {
                        val reviewed = uiState.sessionStats.totalReviewed
                        val statusText = if (uiState.remainingDueCount > 0) {
                            "Reviewed: $reviewed • ${uiState.remainingDueCount} due"
                        } else {
                            "Reviewed: $reviewed"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            uiState.currentCard?.let { card ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Text(
                                        text = "Box ${card.currentBox}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Practice"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            uiState.isSessionFinished || uiState.queue.isEmpty() -> {
                StudyFinishedView(
                    stats = uiState.sessionStats,
                    onReturnHome = onReturnHome,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                val card = uiState.currentCard ?: return@Scaffold

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Main Flashcard Surface (with gesture detection)
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp)
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val showEnglishOnTop = !uiState.isAnswerRevealed &&
                                        (card.quizType == QuizType.CLOZE || card.quizType == QuizType.WORD_ASSEMBLY || card.quizType == QuizType.WRITING)

                                if (showEnglishOnTop) {
                                    val englishText = if (card.english.isNotEmpty()) {
                                        card.english.joinToString("; ")
                                    } else "Complete the word"

                                    Text(
                                        text = englishText,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )

                                    if (card.quizType == QuizType.WRITING && card.wordSlots.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            card.wordSlots.forEachIndexed { idx, slotChar ->
                                                val isTarget = idx == card.clozeTargetIndex
                                                val displayChar = if (isTarget) "_" else slotChar
                                                Text(
                                                    text = displayChar,
                                                    style = AndZiThemeCustom.chineseTypography.hanziLarge,
                                                    color = if (isTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val displayedHanzi = if (uiState.isAnswerRevealed) card.revealedHanzi else card.hanzi
                                    val hanziStyle = when {
                                        displayedHanzi.length <= 1 -> AndZiThemeCustom.chineseTypography.hanziCard
                                        displayedHanzi.length <= 3 -> AndZiThemeCustom.chineseTypography.hanziLarge
                                        else -> AndZiThemeCustom.chineseTypography.hanziMedium
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .detectToneGestures(
                                                enabled = !uiState.isAnswerRevealed && card.quizType == QuizType.TONE,
                                                onToneDetected = { tone -> onToneSelected(tone) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = displayedHanzi,
                                            style = hanziStyle,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                if ((card.quizType == QuizType.TONE || card.quizType == QuizType.PINYIN) && card.contextWords.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (uiState.isContextWordsRevealed || uiState.isAnswerRevealed) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "As in: ${card.contextWords.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = onRevealContextWords,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.as_in),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Contextual Quiz Prompt
                                val promptText = when (card.quizType) {
                                    QuizType.TONE -> if (!uiState.isAnswerRevealed) "Swipe or tap the correct tone" else ""
                                    QuizType.ENGLISH -> "Select the English meaning"
                                    QuizType.CLOZE -> "Fill in the missing character"
                                    QuizType.WORD_ASSEMBLY -> "Assemble the compound word"
                                    QuizType.PINYIN -> "Select the correct Pinyin pronunciation"
                                    QuizType.WRITING -> if (card.wordSlots.isNotEmpty()) "Write the missing character" else "Write the character from memory"
                                }
                                if (promptText.isNotEmpty()) {
                                    Text(
                                        text = promptText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Revealed Feedback Card
                    if (uiState.isAnswerRevealed) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                RevealedFeedbackCard(
                                    card = card,
                                    isCorrect = uiState.isCorrect,
                                    selectedOption = uiState.selectedOption,
                                    onAcceptAnswer = onAcceptAnswer,
                                    onNextCard = { onNextCard() }
                                )
                                if ((card.quizType == QuizType.CLOZE || card.quizType == QuizType.WRITING) && card.relatedStudyWords.isNotEmpty()) {
                                    StudyWordsUsingCharacterSection(
                                        character = card.missingCharacter,
                                        words = card.relatedStudyWords
                                    )
                                }
                            }
                        }
                    } else {
                        // Interactive Quiz Input
                        item {
                            when (card.quizType) {
                                QuizType.TONE -> {
                                    ToneKeypad(
                                        enabled = !uiState.isAnswerRevealed,
                                        onToneSelected = { tone -> onToneSelected(tone) }
                                    )
                                }
                                QuizType.ENGLISH -> {
                                    val correct = card.english.firstOrNull() ?: ""
                                    EnglishMcqView(
                                        options = card.mcqOptions,
                                        correctMeaning = correct,
                                        correctMeanings = card.english,
                                        selectedOption = uiState.selectedOption,
                                        isRevealed = uiState.isAnswerRevealed,
                                        onOptionSelected = { option -> onEnglishSelected(option) }
                                    )
                                }
                                QuizType.CLOZE, QuizType.WORD_ASSEMBLY -> {
                                    ClozeAssemblyView(
                                        quizType = card.quizType,
                                        wordSlots = card.wordSlots,
                                        clozeTargetIndex = card.clozeTargetIndex,
                                        assembledChars = uiState.assembledChars,
                                        usedTileIndices = uiState.usedTileIndices,
                                        tiles = card.scrambledTiles,
                                        isRevealed = uiState.isAnswerRevealed,
                                        isCorrect = uiState.isCorrect,
                                        onTileSelected = { index, tile ->
                                            if (card.quizType == QuizType.CLOZE) {
                                                onClozeSelected(tile)
                                            } else {
                                                onAssemblyTileSelected(index, tile)
                                            }
                                        },
                                        onUndoTile = onUndoAssemblyTile
                                    )
                                }
                                QuizType.PINYIN -> {
                                    PinyinQuizView(
                                        options = card.mcqOptions,
                                        correctPinyin = PinyinParser.convert_to_pretty_version(card.pinyin),
                                        selectedOption = uiState.selectedOption,
                                        isRevealed = uiState.isAnswerRevealed,
                                        onOptionSelected = onPinyinSelected
                                    )
                                }
                                QuizType.WRITING -> {
                                    card.writingCharacter?.let { charData ->
                                        WritingQuizView(
                                            character = charData,
                                            completedStrokes = uiState.completedStrokes,
                                            currentStrokeIndex = uiState.currentStrokeIndex,
                                            scaffoldingLevel = uiState.scaffoldingLevel,
                                            isAnswerRevealed = uiState.isAnswerRevealed,
                                            errorShakeTrigger = uiState.writingErrorShakeTrigger,
                                            onStrokeDrawn = onWritingStrokeDrawn,
                                            onSetScaffoldingLevel = onSetWritingScaffolding,
                                            onUndoStroke = onUndoWritingStroke,
                                            onClearCanvas = onClearWritingCanvas,
                                            onSurrender = onSurrenderWriting
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
