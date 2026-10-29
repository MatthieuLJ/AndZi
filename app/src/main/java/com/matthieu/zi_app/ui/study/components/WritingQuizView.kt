package com.matthieu.zi_app.ui.study.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matthieu.zi_app.domain.writing.HanziCharacter
import com.matthieu.zi_app.domain.writing.HanziPoint
import com.matthieu.zi_app.domain.writing.HanziStroke
import com.matthieu.zi_app.domain.writing.WritingScaffoldingLevel
import com.matthieu.zi_app.ui.study.HanziWritingCanvas

@Composable
fun WritingQuizView(
    character: HanziCharacter,
    completedStrokes: List<HanziStroke>,
    currentStrokeIndex: Int,
    scaffoldingLevel: WritingScaffoldingLevel,
    isAnswerRevealed: Boolean,
    errorShakeTrigger: Int,
    onStrokeDrawn: (List<HanziPoint>) -> Unit,
    onSetScaffoldingLevel: (WritingScaffoldingLevel) -> Unit,
    onUndoStroke: () -> Unit,
    onClearCanvas: () -> Unit,
    onSurrender: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scaffolding Level Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            WritingScaffoldingLevel.entries.forEach { level ->
                val selected = (scaffoldingLevel == level)
                FilterChip(
                    selected = selected,
                    onClick = { onSetScaffoldingLevel(level) },
                    label = {
                        Text(
                            text = when (level) {
                                WritingScaffoldingLevel.TEST -> "Test (Memory)"
                                WritingScaffoldingLevel.GUIDED_FULL -> "L1 Trace"
                                WritingScaffoldingLevel.GUIDED_DIRECTION -> "L2 Guide"
                                WritingScaffoldingLevel.GUIDED_FIRST_STROKE -> "L3 1st Stroke"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Rice Grid Interactive Canvas
        HanziWritingCanvas(
            character = character,
            completedStrokes = completedStrokes,
            currentStrokeIndex = currentStrokeIndex,
            scaffoldingLevel = scaffoldingLevel,
            isAnswerRevealed = isAnswerRevealed,
            errorShakeTrigger = errorShakeTrigger,
            onStrokeDrawn = onStrokeDrawn,
            size = 280.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Stroke Progress Text
        Text(
            text = "Stroke ${minOf(currentStrokeIndex + 1, character.strokeCount)} of ${character.strokeCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Action controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onUndoStroke,
                    enabled = !isAnswerRevealed && completedStrokes.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo stroke",
                        tint = if (completedStrokes.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(
                    onClick = onClearCanvas,
                    enabled = !isAnswerRevealed && completedStrokes.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear canvas",
                        tint = if (completedStrokes.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
            }

            TextButton(
                onClick = onSurrender,
                enabled = !isAnswerRevealed
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Give Up",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

