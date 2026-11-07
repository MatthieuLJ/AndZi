package com.matthieu.zi_app.ui.study.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matthieu.zi_app.Pinyin
import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.ui.study.CollocationPair
import com.matthieu.zi_app.ui.study.QuizType
import com.matthieu.zi_app.ui.study.SessionStats
import com.matthieu.zi_app.ui.study.StudyCard
import com.matthieu.zi_app.ui.theme.AndZiThemeCustom
import com.matthieu.zi_app.ui.theme.buildPinyinToneAnnotatedString

@Composable
fun ToneKeypad(
    enabled: Boolean,
    onToneSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val toneColors = AndZiThemeCustom.toneColors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToneButton(
                toneNumber = 1,
                label = "1st  Flat ¯",
                color = toneColors.tone1,
                enabled = enabled,
                onClick = { onToneSelected(1) },
                modifier = Modifier.weight(1f)
            )
            ToneButton(
                toneNumber = 2,
                label = "2nd  Rising ˊ",
                color = toneColors.tone2,
                enabled = enabled,
                onClick = { onToneSelected(2) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToneButton(
                toneNumber = 3,
                label = "3rd  Dip ˇ",
                color = toneColors.tone3,
                enabled = enabled,
                onClick = { onToneSelected(3) },
                modifier = Modifier.weight(1f)
            )
            ToneButton(
                toneNumber = 4,
                label = "4th  Falling ˋ",
                color = toneColors.tone4,
                enabled = enabled,
                onClick = { onToneSelected(4) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ToneButton(
    toneNumber: Int,
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, color.copy(alpha = if (enabled) 0.8f else 0.3f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$toneNumber",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun EnglishMcqView(
    options: List<String>,
    correctMeaning: String,
    selectedOption: String?,
    isRevealed: Boolean,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    correctMeanings: List<String> = listOf(correctMeaning)
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { option ->
            val isSelected = (selectedOption == option)
            val isCorrect = option.equals(correctMeaning, ignoreCase = true) ||
                    correctMeanings.any { it.equals(option, ignoreCase = true) }

            val backgroundColor by animateColorAsState(
                targetValue = when {
                    !isRevealed -> MaterialTheme.colorScheme.surfaceVariant
                    isCorrect -> Color(0xFF10B981).copy(alpha = 0.25f)
                    isSelected && !isCorrect -> Color(0xFFEF4444).copy(alpha = 0.25f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                label = "mcqColor"
            )

            val borderColor = when {
                !isRevealed && isSelected -> MaterialTheme.colorScheme.primary
                isRevealed && isCorrect -> Color(0xFF10B981)
                isRevealed && isSelected && !isCorrect -> Color(0xFFEF4444)
                else -> Color.Transparent
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                    .clickable(enabled = !isRevealed) { onOptionSelected(option) },
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isRevealed) {
                        if (isCorrect) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Correct",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Incorrect",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinyinQuizView(
    options: List<String>,
    correctSyllable: String,
    correctTone: Int,
    selectedSyllable: String?,
    selectedTone: Int?,
    isRevealed: Boolean,
    onSyllableSelected: (String) -> Unit,
    onToneSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasTone = correctTone in 1..4

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Pinyin Syllable
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasTone) {
                Text(
                    text = "1. Select Pinyin",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val rows = options.chunked(2)
            rows.forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowOptions.forEach { option ->
                        val isSelected = (selectedSyllable == option)
                        val isCorrect = option.equals(correctSyllable, ignoreCase = true)

                        val backgroundColor by animateColorAsState(
                            targetValue = when {
                                !isRevealed -> if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                isCorrect -> Color(0xFF10B981).copy(alpha = 0.25f)
                                isSelected && !isCorrect -> Color(0xFFEF4444).copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            label = "pinyinOptionColor"
                        )

                        val borderColor = when {
                            !isRevealed && isSelected -> MaterialTheme.colorScheme.primary
                            isRevealed && isCorrect -> Color(0xFF10B981)
                            isRevealed && isSelected && !isCorrect -> Color(0xFFEF4444)
                            else -> Color.Transparent
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                                .clickable(enabled = !isRevealed) { onSyllableSelected(option) },
                            colors = CardDefaults.cardColors(containerColor = backgroundColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isRevealed) {
                                    if (isCorrect) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Correct",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Incorrect",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (rowOptions.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Section 2: Tone (Only if card has a tone 1..4)
        if (hasTone) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "2. Select Tone",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val toneColors = AndZiThemeCustom.toneColors
                val toneRows = listOf(
                    listOf(
                        1 to ("1st  Flat ¯" to toneColors.tone1),
                        2 to ("2nd  Rising ˊ" to toneColors.tone2)
                    ),
                    listOf(
                        3 to ("3rd  Dip ˇ" to toneColors.tone3),
                        4 to ("4th  Falling ˋ" to toneColors.tone4)
                    )
                )

                toneRows.forEach { rowTones ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowTones.forEach { (toneNum, labelAndColor) ->
                            val (label, toneColor) = labelAndColor
                            val isSelected = (selectedTone == toneNum)
                            val isCorrect = (toneNum == correctTone)

                            val backgroundColor by animateColorAsState(
                                targetValue = when {
                                    !isRevealed -> if (isSelected) toneColor.copy(alpha = 0.35f) else toneColor.copy(alpha = 0.12f)
                                    isCorrect -> Color(0xFF10B981).copy(alpha = 0.25f)
                                    isSelected && !isCorrect -> Color(0xFFEF4444).copy(alpha = 0.25f)
                                    else -> toneColor.copy(alpha = 0.05f)
                                },
                                label = "pinyinToneColor"
                            )

                            val borderColor = when {
                                !isRevealed && isSelected -> toneColor
                                !isRevealed -> toneColor.copy(alpha = 0.6f)
                                isRevealed && isCorrect -> Color(0xFF10B981)
                                isRevealed && isSelected && !isCorrect -> Color(0xFFEF4444)
                                else -> Color.Transparent
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isRevealed) { onToneSelected(toneNum) },
                                colors = CardDefaults.cardColors(containerColor = backgroundColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isRevealed) {
                                        if (isCorrect) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Correct",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Incorrect",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
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
    }
}

@Composable
fun ClozeAssemblyView(
    quizType: QuizType,
    wordSlots: List<String>,
    clozeTargetIndex: Int,
    assembledChars: List<String>,
    usedTileIndices: List<Int>,
    tiles: List<String>,
    isRevealed: Boolean,
    isCorrect: Boolean?,
    onTileSelected: (Int, String) -> Unit,
    onUndoTile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAssembly = (quizType == QuizType.WORD_ASSEMBLY)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Word Slot Boxes (Target / Assembling word)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            wordSlots.forEachIndexed { index, solutionChar ->
                if (isAssembly) {
                    val hasChar = index < assembledChars.size
                    val displayChar = when {
                        isRevealed -> solutionChar
                        hasChar -> assembledChars[index]
                        else -> ""
                    }
                    val isNextActiveSlot = (index == assembledChars.size && !isRevealed)

                    val borderColor = when {
                        isRevealed && isCorrect == true -> Color(0xFF10B981)
                        isRevealed && isCorrect == false -> Color(0xFFEF4444)
                        isNextActiveSlot -> MaterialTheme.colorScheme.primary
                        hasChar -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else -> Color.Transparent
                    }

                    val bgColor = when {
                        isRevealed && isCorrect == true -> Color(0xFF10B981).copy(alpha = 0.15f)
                        isRevealed && isCorrect == false -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        hasChar -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable(enabled = !isRevealed && hasChar) {
                                onUndoTile()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayChar,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                isRevealed && isCorrect == true -> Color(0xFF10B981)
                                isRevealed && isCorrect == false -> Color(0xFFEF4444)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                } else {
                    // CLOZE Mode: One missing slot
                    val isBlank = (index == clozeTargetIndex)
                    val displayChar = when {
                        !isBlank -> solutionChar
                        isRevealed -> solutionChar
                        else -> "?"
                    }

                    val borderColor = when {
                        isBlank && isRevealed && isCorrect == true -> Color(0xFF10B981)
                        isBlank && isRevealed && isCorrect == false -> Color(0xFFEF4444)
                        isBlank -> MaterialTheme.colorScheme.primary
                        else -> Color.Transparent
                    }

                    val bgColor = when {
                        isBlank && isRevealed && isCorrect == true -> Color(0xFF10B981).copy(alpha = 0.15f)
                        isBlank && isRevealed && isCorrect == false -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        isBlank -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .border(2.dp, borderColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayChar,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                isBlank && isRevealed && isCorrect == true -> Color(0xFF10B981)
                                isBlank && isRevealed && isCorrect == false -> Color(0xFFEF4444)
                                isBlank && !isRevealed -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        // 2. Candidate Character Tiles (Chunked into rows of up to 4 tiles)
        val chunkedTiles = tiles.mapIndexed { idx, char -> idx to char }.chunked(4)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            chunkedTiles.forEach { rowTiles ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowTiles.forEach { (tileIndex, tileChar) ->
                        val maxAllowed = if (isAssembly) {
                            maxOf(1, wordSlots.count { it == tileChar })
                        } else {
                            1
                        }
                        val timesUsed = usedTileIndices.count { it == tileIndex }
                        val isUsed = timesUsed >= maxAllowed
                        val isEnabled = !isRevealed && !isUsed

                        Card(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = isEnabled) { onTileSelected(tileIndex, tileChar) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUsed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isUsed) 0.dp else 3.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tileChar,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (isUsed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PairMatchQuizView(
    leftTiles: List<String>,
    rightTiles: List<String>,
    pairs: List<CollocationPair>,
    matchedWords: Set<String>,
    selectedLeftIndex: Int?,
    selectedRightIndex: Int?,
    errorShakeTrigger: Int,
    onSelectLeft: (Int) -> Unit,
    onSelectRight: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(errorShakeTrigger) {
        if (errorShakeTrigger > 0) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 300
                    0f at 0
                    -12f at 50
                    12f at 100
                    -8f at 150
                    8f at 200
                    -4f at 250
                    0f at 300
                }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset(x = shakeOffset.value.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val tileHeight = when {
            leftTiles.size >= 5 -> 50.dp
            leftTiles.size == 4 -> 56.dp
            else -> 64.dp
        }
        val tileSpacing = if (leftTiles.size >= 5) 8.dp else 12.dp
        val tileTextStyle = if (leftTiles.size >= 5) AndZiThemeCustom.chineseTypography.hanziMedium else AndZiThemeCustom.chineseTypography.hanziLarge

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            // Left Column (1st characters)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(tileSpacing)
            ) {
                leftTiles.forEachIndexed { index, char ->
                    val isMatched = matchedWords.any { it.startsWith(char) }
                    val isSelected = (selectedLeftIndex == index)

                    val borderColor = when {
                        isMatched -> Color(0xFF10B981).copy(alpha = 0.5f)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    }

                    val containerColor = when {
                        isMatched -> Color(0xFF10B981).copy(alpha = 0.12f)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(tileHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !isMatched) { onSelectLeft(index) },
                        shape = RoundedCornerShape(16.dp),
                        color = containerColor,
                        border = BorderStroke(if (isSelected) 2.5.dp else 1.5.dp, borderColor)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                style = tileTextStyle,
                                color = when {
                                    isMatched -> Color(0xFF10B981)
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            // Right Column (2nd characters)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(tileSpacing)
            ) {
                rightTiles.forEachIndexed { index, char ->
                    val isMatched = matchedWords.any { it.endsWith(char) }
                    val isSelected = (selectedRightIndex == index)

                    val borderColor = when {
                        isMatched -> Color(0xFF10B981).copy(alpha = 0.5f)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    }

                    val containerColor = when {
                        isMatched -> Color(0xFF10B981).copy(alpha = 0.12f)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(tileHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !isMatched) { onSelectRight(index) },
                        shape = RoundedCornerShape(16.dp),
                        color = containerColor,
                        border = BorderStroke(if (isSelected) 2.5.dp else 1.5.dp, borderColor)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                style = tileTextStyle,
                                color = when {
                                    isMatched -> Color(0xFF10B981)
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }

        // Matched pairs list display below columns
        if (matchedWords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pairs.filter { it.word in matchedWords }.forEach { pair ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = pair.word,
                                    style = AndZiThemeCustom.chineseTypography.hanziMedium,
                                    color = Color(0xFF10B981)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = pair.pinyin,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = pair.english.firstOrNull() ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RevealedFeedbackCard(
    card: StudyCard,
    isCorrect: Boolean?,
    onNextCard: () -> Unit,
    modifier: Modifier = Modifier,
    selectedOption: String? = null,
    onAcceptAnswer: (() -> Unit)? = null,
    onRateGrade: ((ReviewGrade) -> Unit)? = null
) {
    val toneColors = AndZiThemeCustom.toneColors
    val effectivePinyin = card.displayPinyin.ifBlank { card.pinyin }
    val annotatedPinyin = buildPinyinToneAnnotatedString(effectivePinyin, toneColors)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Correct / Incorrect Banner
            if (isCorrect != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCorrect) "Correct!" else "Needs Review",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Pinyin with Tone Color per syllable or Pair Match list
            if (card.quizType == QuizType.PAIR_MATCH && card.matchPairs.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    card.matchPairs.forEach { pair ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pair.word,
                                        style = AndZiThemeCustom.chineseTypography.hanziMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = buildPinyinToneAnnotatedString(pair.pinyin, toneColors),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                                Text(
                                    text = pair.english.firstOrNull() ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = annotatedPinyin,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                // English Meanings
                if (card.english.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = card.english.joinToString("; "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (isCorrect == false && !selectedOption.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val isChineseText = card.quizType == QuizType.WORD_ASSEMBLY || card.quizType == QuizType.CLOZE
                    val baseStyle = if (isChineseText) {
                        AndZiThemeCustom.chineseTypography.hanziMedium.copy(
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "Your answer: $selectedOption",
                        style = baseStyle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isCorrect == false && card.quizType == QuizType.ENGLISH && onAcceptAnswer != null) {
                OutlinedButton(
                    onClick = onAcceptAnswer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "My answer was acceptable",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Prominent Continue Button
            Button(
                onClick = onNextCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun StudyWordsUsingCharacterSection(
    character: String,
    words: List<Vocabulary>,
    modifier: Modifier = Modifier
) {
    val toneColors = AndZiThemeCustom.toneColors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Study words using $character",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        words.forEach { word ->
            val annotatedPinyin = buildPinyinToneAnnotatedString(word.pinyin, toneColors)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
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
                    Text(
                        text = word.chinese,
                        style = if (word.chinese.length > 3) {
                            AndZiThemeCustom.chineseTypography.hanziMedium.copy(fontSize = 20.sp)
                        } else {
                            AndZiThemeCustom.chineseTypography.hanziMedium
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(min = 60.dp, max = 110.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = annotatedPinyin,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        if (word.english.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = word.english.joinToString("; "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudyFinishedView(
    stats: SessionStats,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Done",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Session Completed!",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Great progress on your daily Chinese review.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.totalReviewed}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Reviewed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.correctCount}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = "Correct",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.accuracyPercentage}%",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Accuracy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onReturnHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Back to Dashboard",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
