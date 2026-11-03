package com.matthieu.zi_app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.matthieu.zi_app.PinyinParser

// --- Material 3 Primary & Secondary Accents ---
val EmeraldPrimaryLight = Color(0xFF059669)
val EmeraldOnPrimaryLight = Color(0xFFFFFFFF)
val EmeraldPrimaryContainerLight = Color(0xFFD1FAE5)
val EmeraldOnPrimaryContainerLight = Color(0xFF064E3B)

val EmeraldPrimaryDark = Color(0xFF10B981)
val EmeraldOnPrimaryDark = Color(0xFF003822)
val EmeraldPrimaryContainerDark = Color(0xFF065F46)
val EmeraldOnPrimaryContainerDark = Color(0xFFA7F3D0)

val CyanSecondaryLight = Color(0xFF0891B2)
val CyanOnSecondaryLight = Color(0xFFFFFFFF)
val CyanSecondaryDark = Color(0xFF22D3EE)
val CyanOnSecondaryDark = Color(0xFF083344)

// --- Background & Surface (Light Mode) ---
val BackgroundLight = Color(0xFFF8FAFC)
val OnBackgroundLight = Color(0xFF0F172A)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val OnSurfaceVariantLight = Color(0xFF475569)
val OutlineLight = Color(0xFFCBD5E1)

// --- Background & Surface (Dark Mode) ---
val BackgroundDark = Color(0xFF121820)
val OnBackgroundDark = Color(0xFFF1F5F9)
val SurfaceDark = Color(0xFF1E2630)
val OnSurfaceDark = Color(0xFFF1F5F9)
val SurfaceVariantDark = Color(0xFF25303D)
val OnSurfaceVariantDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF334155)

// --- Standard Chinese Tone Color Palette ---
@Immutable
data class PinyinToneColors(
    val tone1: Color = Color(0xFFEF4444), // Flat (Red / Crimson)
    val tone2: Color = Color(0xFFF59E0B), // Rising (Orange / Amber)
    val tone3: Color = Color(0xFF10B981), // Dip (Green / Emerald)
    val tone4: Color = Color(0xFF3B82F6), // Falling (Blue / Cobalt)
    val tone5: Color = Color(0xFF94A3B8), // Neutral (Slate / Gray)
    val toneAgain: Color = Color(0xFFEF4444),
    val toneHard: Color = Color(0xFFF59E0B),
    val toneGood: Color = Color(0xFF10B981),
    val toneEasy: Color = Color(0xFF3B82F6)
) {
    fun colorForTone(tone: Int): Color = when (tone) {
        1 -> tone1
        2 -> tone2
        3 -> tone3
        4 -> tone4
        else -> tone5
    }
}

val LightToneColors = PinyinToneColors(
    tone1 = Color(0xFFDC2626),
    tone2 = Color(0xFFD97706),
    tone3 = Color(0xFF059669),
    tone4 = Color(0xFF2563EB),
    tone5 = Color(0xFF64748B)
)

val DarkToneColors = PinyinToneColors(
    tone1 = Color(0xFFF87171),
    tone2 = Color(0xFFFBBF24),
    tone3 = Color(0xFF34D399),
    tone4 = Color(0xFF60A5FA),
    tone5 = Color(0xFF94A3B8)
)

val LocalPinyinToneColors = staticCompositionLocalOf { PinyinToneColors() }

fun buildPinyinToneAnnotatedString(
    pinyinString: String,
    toneColors: PinyinToneColors
): AnnotatedString {
    val clean = pinyinString.trim()
    if (clean.isEmpty()) return AnnotatedString("")

    val regex = Regex("([a-zA-ZāōēīūǖáóéíúǘǎǒěǐǔǚàòèìùǜüÜ'0-5]+|[^a-zA-ZāōēīūǖáóéíúǘǎǒěǐǔǚàòèìùǜüÜ'0-5]+)")
    val matches = regex.findAll(clean)
    return buildAnnotatedString {
        for (match in matches) {
            val token = match.value
            val pinyins = PinyinParser.parse_string_to_pinyin(token)
            if (!pinyins.isNullOrEmpty()) {
                pinyins.forEach { p ->
                    withStyle(SpanStyle(color = toneColors.colorForTone(p.tone))) {
                        append(p.print)
                    }
                }
            } else {
                append(PinyinParser.convert_to_pretty_version(token))
            }
        }
    }
}

// --- Leitner Mastery Box Colors (Box 0 to Box 9) ---
val LeitnerBoxColors = listOf(
    Color(0xFFE91E63), // Box 0: Vivid Red / Magenta
    Color(0xFFE57373), // Box 1: Coral / Light Red
    Color(0xFFFFB74D), // Box 2: Orange
    Color(0xFFFFD54F), // Box 3: Amber / Yellow
    Color(0xFF81D4FA), // Box 4: Sky Blue
    Color(0xFF4FC3F7), // Box 5: Cyan
    Color(0xFF42A5F5), // Box 6: Blue
    Color(0xFF673AB7), // Box 7: Deep Purple
    Color(0xFF66BB6A), // Box 8: Green
    Color(0xFF2E7D32)  // Box 9: Dark Forest Green / Mastered
)

fun colorForLeitnerBox(box: Int): Color {
    return LeitnerBoxColors.getOrElse(box.coerceIn(0, 9)) { LeitnerBoxColors[0] }
}
