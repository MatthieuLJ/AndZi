package com.matthieu.zi_app.ui.podcast.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.data.local.entity.PodcastChannelEntity
import com.matthieu.zi_app.data.local.entity.PodcastSentenceEntity
import com.matthieu.zi_app.ui.dictionary.WordDetailSheetContent
import com.matthieu.zi_app.ui.podcast.components.PodcastAttributionDialog
import com.matthieu.zi_app.ui.theme.AndZiThemeCustom
import com.matthieu.zi_app.ui.theme.buildPinyinToneAnnotatedString
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastPlayerScreen(
    viewModel: PodcastPlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wordSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val vocabSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showAttributionDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.pauseAudio()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.episode?.title ?: "Story Player",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        uiState.channel?.let { ch ->
                            Text(
                                text = ch.chineseTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Podcast Source & Attribution Info Trigger
                    IconButton(onClick = { showAttributionDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Podcast Attribution"
                        )
                    }
                    // Vocabulary Sheet Trigger with Badge
                    IconButton(onClick = { viewModel.setShowVocabSheet(true) }) {
                        BadgedBox(
                            badge = {
                                if (uiState.vocabItems.isNotEmpty()) {
                                    Badge { Text(uiState.vocabItems.size.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "Episode Vocabulary"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Dual Listening Mode Tabs
                TabRow(
                    selectedTabIndex = if (uiState.listeningMode == ListeningMode.READ_AND_LISTEN) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = uiState.listeningMode == ListeningMode.READ_AND_LISTEN,
                        onClick = { viewModel.setListeningMode(ListeningMode.READ_AND_LISTEN) },
                        text = { Text("Read & Listen") }
                    )
                    Tab(
                        selected = uiState.listeningMode == ListeningMode.BLIND,
                        onClick = { viewModel.setListeningMode(ListeningMode.BLIND) },
                        text = { Text("Blind Listening") }
                    )
                }

                // Audio Player Controls Card
                AudioPlayerCard(
                    playerState = uiState.playerState,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onSeekBy = { viewModel.seekBy(it) },
                    onSetSpeed = { viewModel.setPlaybackSpeed(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )

                // Main Content Area: Transcript or Blind listening view
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (uiState.listeningMode == ListeningMode.BLIND) {
                        BlindListeningView(
                            onPeekTranscript = { viewModel.setListeningMode(ListeningMode.READ_AND_LISTEN) }
                        )
                    } else {
                        TranscriptReaderView(
                            sentences = uiState.sentences,
                            rawTranscript = uiState.episode?.transcript ?: "",
                            currentPositionMs = uiState.playerState.currentPositionMs,
                            isPlaying = uiState.playerState.isPlaying,
                            channel = uiState.channel,
                            onSeekTo = { viewModel.seekTo(it) },
                            onWordClick = { word -> viewModel.selectWordForDetail(word) },
                            onAttributionClick = { showAttributionDialog = true }
                        )
                    }
                }
            }
        }

        // Vocabulary List Modal Bottom Sheet
        if (uiState.showVocabSheet) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setShowVocabSheet(false) },
                sheetState = vocabSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                EpisodeVocabSheetContent(
                    vocabItems = uiState.vocabItems,
                    selectedTab = uiState.selectedVocabTab,
                    onSelectTab = { viewModel.setVocabFilterTab(it) },
                    onItemClick = { vocab -> viewModel.selectWordForDetail(vocab.chinese) },
                    onToggleStudy = { vocab -> viewModel.toggleStudyForVocabItem(vocab) }
                )
            }
        }

        // Detailed Character / Word Modal Bottom Sheet
        if (uiState.selectedWord != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearWordSelection() },
                sheetState = wordSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                WordDetailSheetContent(
                    word = uiState.selectedWord!!,
                    polyReadings = uiState.polyReadings,
                    constituents = uiState.constituents,
                    compounds = uiState.compounds,
                    isInStudy = uiState.isInStudy,
                    currentBox = uiState.currentBox,
                    selectedTranslations = uiState.selectedTranslations,
                    onToggleStudy = { viewModel.toggleStudyForWord(uiState.selectedWord!!) },
                    onToggleTranslation = { def -> viewModel.toggleTranslationForStudy(def) },
                    onAddCustomTranslation = { customDef -> viewModel.addCustomTranslation(customDef) },
                    onSelectConstituent = { char -> viewModel.selectWordForDetail(char) },
                    onSelectCompound = { compound -> viewModel.selectWordForDetail(compound.chinese) }
                )
            }
        }

        if (showAttributionDialog) {
            PodcastAttributionDialog(
                onDismissRequest = { showAttributionDialog = false },
                targetChannelTitle = uiState.channel?.title
            )
        }
    }
}

@Composable
fun AudioPlayerCard(
    playerState: AudioPlayerState,
    onTogglePlay: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onSeekBy: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Seekbar
            var sliderPosition by remember { mutableFloatStateOf(0f) }
            var isUserDragging by remember { mutableStateOf(false) }

            val currentMs = if (isUserDragging) {
                (sliderPosition * playerState.durationMs).toInt()
            } else {
                playerState.currentPositionMs
            }

            val progressRatio = if (playerState.durationMs > 0) {
                playerState.currentPositionMs.toFloat() / playerState.durationMs.toFloat()
            } else 0f

            Slider(
                value = if (isUserDragging) sliderPosition else progressRatio.coerceIn(0f, 1f),
                onValueChange = {
                    isUserDragging = true
                    sliderPosition = it
                },
                onValueChangeFinished = {
                    val targetMs = (sliderPosition * playerState.durationMs).toInt()
                    onSeekTo(targetMs)
                    isUserDragging = false
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Duration labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(playerState.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main playback buttons & Speed controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback speed chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.8f, 1.0f, 1.2f).forEach { speed ->
                        val isSelected = playerState.speed == speed
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSetSpeed(speed) }
                        ) {
                            Text(
                                text = "${speed}x",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Transport Controls: -5s, Play/Pause, +10s
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { onSeekBy(-5000) }) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 5s",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    IconButton(onClick = { onSeekBy(10000) }) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BlindListeningView(
    onPeekTranscript: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Blind Listening Mode",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The transcript is hidden to train pure auditory comprehension. Focus on identifying tones, words, and meaning by ear.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onPeekTranscript) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Show Transcript")
            }
        }
    }
}

@Composable
fun TranscriptReaderView(
    sentences: List<PodcastSentenceEntity>,
    rawTranscript: String,
    currentPositionMs: Int,
    isPlaying: Boolean,
    onSeekTo: (Int) -> Unit,
    onWordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    channel: PodcastChannelEntity? = null,
    onAttributionClick: () -> Unit = {}
) {
    if (sentences.isEmpty()) {
        // Fallback to paragraph view if sentences are not yet available
        val paragraphs = remember(rawTranscript) {
            rawTranscript.split("\n\n").filter { it.isNotBlank() }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            paragraphs.forEach { para ->
                Text(
                    text = para.trim(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 19.sp,
                        lineHeight = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            TranscriptAttributionFooter(
                channel = channel,
                onAttributionClick = onAttributionClick
            )
        }
        return
    }

    // Active sentence index based on audio player position
    val activeSentenceIndex = remember(sentences, currentPositionMs) {
        sentences.indexOfFirst { currentPositionMs in it.startMs..it.endMs }
    }

    val listState = rememberLazyListState()

    // Smoothly scroll active sentence into view when playing, unless user is actively dragging
    LaunchedEffect(activeSentenceIndex, isPlaying) {
        if (isPlaying && activeSentenceIndex >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(
                index = activeSentenceIndex,
                scrollOffset = -100 // Center slightly from top
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        itemsIndexed(
            items = sentences,
            key = { _, item -> item.sentenceId.takeIf { it != 0 } ?: item.sentenceIdx }
        ) { index, sentence ->
            val isActive = index == activeSentenceIndex

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                tonalElevation = if (isActive) 2.dp else 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSeekTo(sentence.startMs) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left indicator bar for active sentence
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = sentence.chineseText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 19.sp,
                            lineHeight = 32.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            TranscriptAttributionFooter(
                channel = channel,
                onAttributionClick = onAttributionClick
            )
        }
    }
}

@Composable
private fun TranscriptAttributionFooter(
    channel: PodcastChannelEntity?,
    onAttributionClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onAttributionClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        val channelLabel = channel?.let { "${it.chineseTitle} (${it.title})" } ?: "Podcast Source"
        Text(
            text = "Story source: $channelLabel • View Attribution & License",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun EpisodeVocabSheetContent(
    vocabItems: List<PodcastVocabItemUiState>,
    selectedTab: VocabFilterTab,
    onSelectTab: (VocabFilterTab) -> Unit,
    onItemClick: (com.matthieu.zi_app.data.local.entity.PodcastVocabEntity) -> Unit,
    onToggleStudy: (com.matthieu.zi_app.data.local.entity.PodcastVocabEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(vocabItems, selectedTab) {
        when (selectedTab) {
            VocabFilterTab.ALL -> vocabItems
            VocabFilterTab.UNLEARNED -> vocabItems.filter { !it.isStudied }
            VocabFilterTab.STUDIED -> vocabItems.filter { it.isStudied }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Episode Vocabulary (${vocabItems.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == VocabFilterTab.ALL,
                onClick = { onSelectTab(VocabFilterTab.ALL) },
                label = { Text("All (${vocabItems.size})") }
            )
            FilterChip(
                selected = selectedTab == VocabFilterTab.UNLEARNED,
                onClick = { onSelectTab(VocabFilterTab.UNLEARNED) },
                label = { Text("Unlearned (${vocabItems.count { !it.isStudied }})") }
            )
            FilterChip(
                selected = selectedTab == VocabFilterTab.STUDIED,
                onClick = { onSelectTab(VocabFilterTab.STUDIED) },
                label = { Text("Studied (${vocabItems.count { it.isStudied }})") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No words in this category",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.vocab.vocabId }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onItemClick(item.vocab) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.vocab.chinese,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (item.vocab.pinyin.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = buildPinyinToneAnnotatedString(item.vocab.pinyin, AndZiThemeCustom.toneColors),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                        )
                                    }
                                }
                                if (item.vocab.translation.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.vocab.translation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Add / Studied toggle action
                            IconButton(onClick = { onToggleStudy(item.vocab) }) {
                                if (item.isStudied) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "In study deck",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add to study deck",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Int): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

