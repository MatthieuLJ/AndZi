package com.matthieu.zi_app.ui.dictionary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.ui.theme.AndZiThemeCustom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search Input Header
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search Hanzi, Pinyin (ni3hao3), or English...",
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

            Spacer(modifier = Modifier.height(12.dp))

            when {
                uiState.isSearching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                uiState.results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.query.isEmpty()) "Type to search CC-CEDICT Dictionary" else "No matching words found",
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
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.results, key = { "${it.chinese}_${it.pinyin}" }) { word ->
                            DictionaryResultCard(
                                word = word,
                                onClick = { viewModel.selectWord(word) }
                            )
                        }
                    }
                }
            }
        }

        // Detailed Character / Word Bottom Sheet
        if (uiState.selectedWord != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelection() },
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
                    onToggleStudy = { viewModel.toggleAddToStudy(uiState.selectedWord!!) },
                    onToggleTranslation = { viewModel.toggleTranslationForStudy(it) },
                    onSelectConstituent = { viewModel.selectCharacterFromConstituent(it) },
                    onSelectCompound = { viewModel.selectWord(it) },
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }
    }
}

@Composable
fun DictionaryResultCard(
    word: Vocabulary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prettyPinyin = PinyinParser.convert_to_pretty_version(word.pinyin)

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
            // Chinese Character / Word
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
                    text = prettyPinyin,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = word.english.joinToString("; "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordDetailSheetContent(
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
    modifier: Modifier = Modifier
) {
    val prettyPinyin = PinyinParser.convert_to_pretty_version(word.pinyin)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
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
                    text = prettyPinyin,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )

                if (currentBox != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "SRS: Box $currentBox",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Quick Add to Study Button
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
                                text = PinyinParser.convert_to_pretty_version(compound.pinyin),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
