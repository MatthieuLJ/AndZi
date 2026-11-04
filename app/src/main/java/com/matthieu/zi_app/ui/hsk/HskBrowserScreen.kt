package com.matthieu.zi_app.ui.hsk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.ui.dictionary.WordDetailSheetContent
import com.matthieu.zi_app.ui.theme.AndZiThemeCustom
import com.matthieu.zi_app.ui.theme.buildPinyinToneAnnotatedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HskBrowserScreen(
    viewModel: HskBrowserViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentLevelProgress = uiState.levelStats[uiState.selectedLevel]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "HSK Vocabulary",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Level Selector Row (HSK 1 to 7)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (lvl in 1..7) {
                    val isSelected = uiState.selectedLevel == lvl
                    val label = if (lvl == 7) "HSK 7+" else "HSK $lvl"
                    val progress = uiState.levelStats[lvl]

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectLevel(lvl) },
                        label = {
                            Text(
                                text = if (progress != null && progress.totalWords > 0) {
                                    "$label (${progress.percentageInt}%)"
                                } else label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Level Progress Card
            if (currentLevelProgress != null && currentLevelProgress.totalWords > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HSK ${uiState.selectedLevel} Progress",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${currentLevelProgress.studiedWords} / ${currentLevelProgress.totalWords} studied (${currentLevelProgress.percentageInt}%)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { currentLevelProgress.percentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            strokeCap = StrokeCap.Round,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Filter Chips & Search Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HskFilter.entries.forEach { filter ->
                    val isSelected = uiState.selectedFilter == filter
                    val countText = when (filter) {
                        HskFilter.ALL -> currentLevelProgress?.totalWords?.toString()
                        HskFilter.UNLEARNED -> currentLevelProgress?.unlearnedWords?.toString()
                        HskFilter.STUDIED -> currentLevelProgress?.studiedWords?.toString()
                    }
                    val displayText = if (countText != null) "${filter.label} ($countText)" else filter.label

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectFilter(filter) },
                        label = { Text(displayText) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Search within level
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search HSK ${uiState.selectedLevel} words...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Word List
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                uiState.words.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                uiState.searchQuery.isNotEmpty() -> "No matching words found"
                                uiState.selectedFilter == HskFilter.UNLEARNED -> "🎉 All HSK ${uiState.selectedLevel} words are in your study deck!"
                                uiState.selectedFilter == HskFilter.STUDIED -> "No HSK ${uiState.selectedLevel} words studied yet."
                                else -> "No words available"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.words, key = { "${it.vocabulary.chinese}_${it.vocabulary.pinyin}" }) { entry ->
                            HskWordCard(
                                entry = entry,
                                onClick = { viewModel.selectWord(entry.vocabulary) },
                                onToggleStudy = { viewModel.toggleStudy(entry) }
                            )
                        }
                    }
                }
            }
        }

        // Detailed Character / Word Modal Bottom Sheet
        if (uiState.selectedWord != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearWordSelection() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                WordDetailSheetContent(
                    word = uiState.selectedWord!!,
                    polyReadings = uiState.selectedCharReadings,
                    constituents = uiState.constituentCharacters,
                    compounds = uiState.containingCompounds,
                    isInStudy = uiState.isInStudyQueue,
                    currentBox = uiState.currentBox,
                    selectedTranslations = uiState.selectedTranslations,
                    onToggleStudy = {
                        val matchingEntry = uiState.words.find { it.vocabulary.chinese == uiState.selectedWord!!.chinese }
                        if (matchingEntry != null) {
                            viewModel.toggleStudy(matchingEntry)
                        } else {
                            viewModel.toggleStudy(
                                HskWordEntry(
                                    vocabulary = uiState.selectedWord!!,
                                    hskLevel = uiState.selectedLevel,
                                    isStudied = uiState.isInStudyQueue
                                )
                            )
                        }
                    },
                    onToggleTranslation = { viewModel.toggleTranslationForStudy(it) },
                    onAddCustomTranslation = { viewModel.addCustomTranslation(it) },
                    onSelectConstituent = { viewModel.onSearchQueryChange(it) },
                    onSelectCompound = { viewModel.selectWord(it) }
                )
            }
        }
    }
}

@Composable
fun HskWordCard(
    entry: HskWordEntry,
    onClick: () -> Unit,
    onToggleStudy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val word = entry.vocabulary
    val annotatedPinyin = buildPinyinToneAnnotatedString(word.pinyin, AndZiThemeCustom.toneColors)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chinese Hanzi
            Text(
                text = word.chinese,
                style = if (word.chinese.length > 3) AndZiThemeCustom.chineseTypography.hanziMedium.copy(fontSize = 20.sp) else AndZiThemeCustom.chineseTypography.hanziMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 55.dp, max = 95.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Pinyin & Translation
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = annotatedPinyin,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                if (word.english.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = word.english.joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action: In Deck status vs. Add button
            if (entry.isStudied) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.clickable { onToggleStudy() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "In Deck",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (entry.leitnerBox != null) "Box ${entry.leitnerBox}" else "In Deck",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onToggleStudy,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

