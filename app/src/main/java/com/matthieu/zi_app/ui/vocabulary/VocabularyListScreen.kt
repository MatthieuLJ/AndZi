package com.matthieu.zi_app.ui.vocabulary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.ui.theme.AndZiThemeCustom
import com.matthieu.zi_app.ui.theme.buildPinyinToneAnnotatedString
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyListScreen(
    viewModel: VocabularyListViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentTab = uiState.currentTab
    val isWordsTab = currentTab == ActiveCardTab.WORDS

    val titleText = if (isWordsTab) "Active Word Cards" else "Active Character Cards"
    val subtitleText = if (isWordsTab) {
        if (uiState.items.isNotEmpty()) {
            if (uiState.filteredItems.size != uiState.items.size) {
                "${uiState.filteredItems.size} of ${uiState.items.size} word cards in study"
            } else {
                "${uiState.items.size} word cards in study"
            }
        } else {
            "No word cards in study"
        }
    } else {
        if (uiState.characterItems.isNotEmpty()) {
            if (uiState.filteredCharacterItems.size != uiState.characterItems.size) {
                "${uiState.filteredCharacterItems.size} of ${uiState.characterItems.size} character cards in study"
            } else {
                "${uiState.characterItems.size} character cards in study"
            }
        } else {
            "No character cards in study"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // Tabs: Word Cards vs Character Cards
            TabRow(
                selectedTabIndex = if (isWordsTab) 0 else 1,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = isWordsTab,
                    onClick = { viewModel.selectTab(ActiveCardTab.WORDS) },
                    text = {
                        Text(
                            text = "Word Cards (${uiState.items.size})",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isWordsTab) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
                Tab(
                    selected = !isWordsTab,
                    onClick = { viewModel.selectTab(ActiveCardTab.CHARACTERS) },
                    text = {
                        Text(
                            text = "Character Cards (${uiState.characterItems.size})",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (!isWordsTab) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Input Header
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = if (isWordsTab) {
                            "Search active word cards, pinyin, English..."
                        } else {
                            "Search characters, pinyin, containing words..."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            val currentTabTotal = if (isWordsTab) uiState.items.size else uiState.characterItems.size
            if (currentTabTotal > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                BoxFilterSection(
                    minAvailableBox = uiState.minAvailableBox,
                    maxAvailableBox = uiState.maxAvailableBox,
                    selectedRange = uiState.selectedBoxRange,
                    boxCounts = uiState.boxCounts,
                    totalCount = currentTabTotal,
                    onRangeChanged = { viewModel.onBoxRangeChanged(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                isWordsTab -> {
                    if (uiState.filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.items.isEmpty()) {
                                    "No active word cards in study yet.\nSearch the Dictionary to add words!"
                                } else {
                                    "No matching active word cards found"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                uiState.filteredItems,
                                key = { "${it.vocabulary.chinese}_${it.vocabulary.pinyin}_${it.leitnerState?.box}" }
                            ) { item ->
                                ActiveVocabularyCard(
                                    item = item,
                                    onClick = { viewModel.selectItem(item) }
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (uiState.filteredCharacterItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.characterItems.isEmpty()) {
                                    "No active character cards in study yet."
                                } else {
                                    "No matching active character cards found"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                uiState.filteredCharacterItems,
                                key = { "char_${it.character}_${it.leitnerState?.box}" }
                            ) { item ->
                                ActiveCharacterCard(
                                    item = item,
                                    onClick = { viewModel.selectCharacter(item.character) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Detailed Word & SRS Edit Bottom Sheet
        if (uiState.selectedItem != null && uiState.selectedWord != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelection() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                ActiveWordDetailSheetContent(
                    word = uiState.selectedWord!!,
                    polyReadings = uiState.selectedCharReadings,
                    constituents = uiState.constituentCharacters,
                    compounds = uiState.containingCompounds,
                    isInStudy = uiState.isInStudyQueue,
                    currentBox = uiState.currentBox,
                    selectedTranslations = uiState.selectedTranslations,
                    onToggleStudy = { viewModel.toggleAddToStudy(uiState.selectedWord!!) },
                    onToggleTranslation = { viewModel.toggleTranslationForStudy(it) },
                    onAddCustomTranslation = { viewModel.addCustomTranslation(it) },
                    onSelectConstituent = { viewModel.selectCharacterFromConstituent(it) },
                    onSelectCompound = { viewModel.selectWordFromCompound(it) },
                    onResetSrs = { viewModel.resetSrsScore(uiState.selectedWord!!) },
                    onRemoveFromStudy = { viewModel.removeFromStudy(uiState.selectedWord!!) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxFilterSection(
    minAvailableBox: Int,
    maxAvailableBox: Int,
    selectedRange: IntRange?,
    boxCounts: Map<Int, Int>,
    totalCount: Int,
    onRangeChanged: (IntRange?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalCount == 0) return

    val hasMultipleBoxes = minAvailableBox < maxAvailableBox
    val effectiveRange = selectedRange ?: (minAvailableBox..maxAvailableBox)

    var sliderRange by remember(effectiveRange, minAvailableBox, maxAvailableBox) {
        mutableStateOf(effectiveRange.first.toFloat()..effectiveRange.last.toFloat())
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header: Filter title, current selection label, clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Box Filter",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Filter by Box",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rangeText = when {
                        selectedRange == null -> "All (Box $minAvailableBox–$maxAvailableBox)"
                        selectedRange.first == selectedRange.last -> "Box ${selectedRange.first}"
                        else -> "Box ${selectedRange.first}–${selectedRange.last}"
                    }
                    Text(
                        text = rangeText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selectedRange != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedRange != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onRangeChanged(null) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Box Filter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // RangeSlider only when minAvailableBox < maxAvailableBox
            if (hasMultipleBoxes) {
                Spacer(modifier = Modifier.height(4.dp))
                RangeSlider(
                    value = sliderRange,
                    onValueChange = { newRange ->
                        sliderRange = newRange
                        val start = newRange.start.roundToInt()
                        val end = newRange.endInclusive.roundToInt()
                        if (selectedRange == null ||
                            start != selectedRange.first ||
                            end != selectedRange.last
                        ) {
                            onRangeChanged(start..end)
                        }
                    },
                    valueRange = minAvailableBox.toFloat()..maxAvailableBox.toFloat(),
                    steps = (maxAvailableBox - minAvailableBox - 1).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(if (hasMultipleBoxes) 4.dp else 8.dp))

            // Quick Box Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All" chip
                val isAllSelected = selectedRange == null
                BoxChip(
                    label = "All",
                    count = totalCount,
                    isSelected = isAllSelected,
                    onClick = { onRangeChanged(null) }
                )

                // Only show chips for boxes that actually have things!
                val boxesWithItems = boxCounts.keys.sorted()
                boxesWithItems.forEach { b ->
                    val count = boxCounts[b] ?: 0
                    val isChipSelected = selectedRange != null && b in selectedRange
                    BoxChip(
                        label = "Box $b",
                        count = count,
                        boxNumber = b,
                        isSelected = isChipSelected,
                        onClick = {
                            if (selectedRange != null && selectedRange.first == b && selectedRange.last == b) {
                                onRangeChanged(null) // toggle off
                            } else {
                                onRangeChanged(b..b)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxChip(
    label: String,
    count: Int,
    boxNumber: Int? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val baseContainerColor = if (boxNumber != null) {
        when (boxNumber) {
            0 -> MaterialTheme.colorScheme.errorContainer
            in 1..3 -> MaterialTheme.colorScheme.tertiaryContainer
            in 4..6 -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val baseTextColor = if (boxNumber != null) {
        when (boxNumber) {
            0 -> MaterialTheme.colorScheme.onErrorContainer
            in 1..3 -> MaterialTheme.colorScheme.onTertiaryContainer
            in 4..6 -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val chipBg = if (isSelected) {
        baseContainerColor
    } else {
        MaterialTheme.colorScheme.surface
    }

    val chipTextColor = if (isSelected) {
        baseTextColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val borderStroke = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = chipBg,
        border = borderStroke,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = chipTextColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isSelected) chipTextColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = chipTextColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun ActiveVocabularyCard(
    item: ActiveVocabularyItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val word = item.vocabulary
    val box = item.leitnerState?.box
    val annotatedPinyin = buildPinyinToneAnnotatedString(word.pinyin, AndZiThemeCustom.toneColors)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chinese Characters
            Text(
                text = word.chinese,
                style = if (word.chinese.length > 3) AndZiThemeCustom.chineseTypography.hanziMedium.copy(fontSize = 20.sp) else AndZiThemeCustom.chineseTypography.hanziMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 60.dp, max = 110.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Pinyin & English
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = annotatedPinyin,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (word.english.isNotEmpty()) word.english.joinToString("; ") else "(No definition)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Box Badge
            val boxText = when (box) {
                null -> "New"
                0 -> "Box 0"
                9 -> "Box 9"
                else -> "Box $box"
            }
            val boxColor = when (box) {
                null, 0 -> MaterialTheme.colorScheme.errorContainer
                in 1..3 -> MaterialTheme.colorScheme.tertiaryContainer
                in 4..6 -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
            val boxTextColor = when (box) {
                null, 0 -> MaterialTheme.colorScheme.onErrorContainer
                in 1..3 -> MaterialTheme.colorScheme.onTertiaryContainer
                in 4..6 -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = boxColor
            ) {
                Text(
                    text = boxText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = boxTextColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ActiveCharacterCard(
    item: ActiveCharacterItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val char = item.character
    val box = item.leitnerState?.box
    val annotatedPinyin = if (item.pinyins.isNotEmpty()) {
        buildAnnotatedString {
            item.pinyins.forEachIndexed { index, p ->
                if (index > 0) append(", ")
                append(buildPinyinToneAnnotatedString(p.trim(), AndZiThemeCustom.toneColors))
            }
        }
    } else {
        AnnotatedString("")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chinese Character
            Text(
                text = char,
                style = AndZiThemeCustom.chineseTypography.hanziMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 54.dp, max = 80.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Pinyin & Containing Words
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (item.pinyins.isNotEmpty()) annotatedPinyin else AnnotatedString("(No pinyin)"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (item.containingWords.isNotEmpty()) {
                    Text(
                        text = "In words: " + item.containingWords.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Single character card",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Box Badge
            val boxText = when (box) {
                null -> "New"
                0 -> "Box 0"
                9 -> "Box 9"
                else -> "Box $box"
            }
            val boxColor = when (box) {
                null, 0 -> MaterialTheme.colorScheme.errorContainer
                in 1..3 -> MaterialTheme.colorScheme.tertiaryContainer
                in 4..6 -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
            val boxTextColor = when (box) {
                null, 0 -> MaterialTheme.colorScheme.onErrorContainer
                in 1..3 -> MaterialTheme.colorScheme.onTertiaryContainer
                in 4..6 -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = boxColor
            ) {
                Text(
                    text = boxText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = boxTextColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveWordDetailSheetContent(
    word: Vocabulary,
    polyReadings: List<Vocabulary>,
    constituents: List<String>,
    compounds: List<Vocabulary>,
    isInStudy: Boolean,
    currentBox: Int?,
    selectedTranslations: Set<String>,
    onToggleStudy: () -> Unit,
    onToggleTranslation: (String) -> Unit,
    onSelectConstituent: (String) -> Unit,
    onSelectCompound: (Vocabulary) -> Unit,
    onRemoveFromStudy: () -> Unit,
    onResetSrs: () -> Unit = {},
    modifier: Modifier = Modifier,
    onAddCustomTranslation: (String) -> Unit = {}
) {
    val annotatedPinyin = buildPinyinToneAnnotatedString(word.pinyin, AndZiThemeCustom.toneColors)
    var customTranslationInput by remember(word.chinese, word.pinyin) { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
    ) {
        // Top Overview Row: Hanzi + Pinyin stacked vertically on the left, Study button on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                val hanziStyle = when {
                    word.chinese.length <= 1 -> AndZiThemeCustom.chineseTypography.hanziCard
                    word.chinese.length <= 3 -> AndZiThemeCustom.chineseTypography.hanziLarge
                    else -> AndZiThemeCustom.chineseTypography.hanziMedium
                }

                // Chinese Characters
                Text(
                    text = word.chinese,
                    style = hanziStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Pinyin directly below the Chinese word
                Text(
                    text = annotatedPinyin,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )

                if (currentBox != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val boxColor = when (currentBox) {
                        0 -> MaterialTheme.colorScheme.errorContainer
                        in 1..3 -> MaterialTheme.colorScheme.tertiaryContainer
                        in 4..6 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val boxTextColor = when (currentBox) {
                        0 -> MaterialTheme.colorScheme.onErrorContainer
                        in 1..3 -> MaterialTheme.colorScheme.onTertiaryContainer
                        in 4..6 -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = boxColor
                    ) {
                        Text(
                            text = "SRS: Box $currentBox",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = boxTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Quick Add/Reset to Study Button
            Button(
                onClick = onToggleStudy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInStudy) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                    contentColor = if (isInStudy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = if (isInStudy) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = if (isInStudy) "Studying" else "Study")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(12.dp))

        // English Definitions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Definitions",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tap definition to add/remove",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (word.english.isEmpty()) {
            Text(
                text = "No definitions available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            word.english.forEachIndexed { index, def ->
                val isSelected = selectedTranslations.contains(def)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleTranslation(def) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Add,
                            contentDescription = if (isSelected) "Included in study" else "Tap to include in study",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${index + 1}. $def",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "In Study",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom English Translation Input
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = customTranslationInput,
                onValueChange = { customTranslationInput = it },
                placeholder = {
                    Text(
                        text = "Add custom translation...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (customTranslationInput.isNotBlank()) {
                            onAddCustomTranslation(customTranslationInput)
                            customTranslationInput = ""
                        }
                    }
                ),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (customTranslationInput.isNotBlank()) {
                        onAddCustomTranslation(customTranslationInput)
                        customTranslationInput = ""
                    }
                },
                enabled = customTranslationInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add custom translation",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Add")
            }
        }

        // Action Buttons Row: Reset SRS and Remove from Study
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onResetSrs,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Reset SRS", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = onRemoveFromStudy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Remove", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Constituent Characters (for multi-character compound words)
        if (constituents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Constituent Characters",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                constituents.forEach { char ->
                    Card(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectConstituent(char) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = char,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // Containing Compound Words (for single characters)
        if (compounds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Common Compound Words (${compounds.size})",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                compounds.take(12).forEach { compound ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectCompound(compound) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = compound.chinese,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = buildPinyinToneAnnotatedString(compound.pinyin, AndZiThemeCustom.toneColors),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
