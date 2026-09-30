package com.matthieu.zi_app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
