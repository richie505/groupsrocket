package com.appsc.prep.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object C {
    val Navy = Color(0xFF0F2557)
    val Ink = Color(0xFF12203A)
    val Body = Color(0xFF1F2937)
    val Muted = Color(0xFF5A6B88)
    val Faint = Color(0xFF8A96AD)
    val Accent = Color(0xFF3949AB)
    val AccentSoft = Color(0xFFE8EAF6)
    val Blue = Color(0xFF1D4ED8)
    val Line = Color(0xFFE5E8EF)
    val Chip = Color(0xFFF1F3F6)
    val Surface = Color(0xFFF7F8FA)
    val ExamBg = Color(0xFFFFF4E8)
    val ExamInk = Color(0xFF9A3412)
    val SeeBg = Color(0xFFF3EEFF)
    val SeeInk = Color(0xFF6D28D9)
    val Green = Color(0xFF15803D)
    val GreenSoft = Color(0xFFE7F6EC)
    val High = Color(0xFFC62828)
    val HighSoft = Color(0xFFFDECEC)
    val Med = Color(0xFFB45309)
    val MedSoft = Color(0xFFFFF4E0)
    val Light = Color(0xFF4B5563)
    val LightSoft = Color(0xFFF1F3F6)
    val FabSoft = Color(0xFFA9C1F0)
}

private val colors = lightColorScheme(
    primary = C.Accent,
    onPrimary = Color.White,
    primaryContainer = C.AccentSoft,
    onPrimaryContainer = C.Navy,
    secondary = C.Navy,
    background = Color.White,
    onBackground = C.Ink,
    surface = Color.White,
    onSurface = C.Ink,
    surfaceVariant = C.Surface,
    onSurfaceVariant = C.Muted,
    outline = C.Line,
    outlineVariant = C.Line,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
)

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = Color.Black),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun PrepTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
