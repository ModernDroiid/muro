package app.muro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.muro.R

/** Colores medidos directamente sobre los PNG del mockup. */
object MuroColors {
    val Background = Color(0xFF101418)
    val Surface = Color(0xFF1A2026)
    val Border = Color(0xFF262F38)
    val ChipBorder = Color(0xFF2E3842)
    val NavBackground = Color(0xFF151A20)
    val NavBorder = Color(0xFF232B33)

    val Text = Color(0xFFE8EDF2)
    val Muted = Color(0xFF9AA6B2)
    val Dim = Color(0xFF8592A0)
    val Placeholder = Color(0xFF757575)

    val Accent = Color(0xFF4ADE80)
    val Toggle = Color(0xFF22C55E)
    val ToggleOff = Color(0xFF3A444F)
    val IconTile = Color(0xFF14532D)
    val Danger = Color(0xFFF87171)

    val Amber = Color(0xFFFBBF24)
    val WarningBackground = Color(0xFF1E1A12)
    val WarningBorder = Color(0xFF4A3B1A)
    val WarningText = Color(0xFFE8DCC0)

    /** Fondos para apps sin icono, tomados de los cuadros de letras del mockup. */
    val Tiles = listOf(
        Color(0xFFFBBF24), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFA78BFA),
        Color(0xFFFB923C), Color(0xFF2DD4BF), Color(0xFFF87171),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun variable(resource: Int, weight: FontWeight) = Font(
    resId = resource,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

val InterFont = FontFamily(weights.map { variable(R.font.inter, it) })
val DisplayFont = FontFamily(weights.map { variable(R.font.space_grotesk, it) })
val MonoFont = FontFamily(weights.map { variable(R.font.jetbrains_mono, it) })

object MuroText {
    val ScreenTitle = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp)
    val Brand = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    val HeroTitle = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
    val Stat = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp)

    val Section = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    val RowTitle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.sp)
    val Body = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp)
    val Small = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp)
    val Caption = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val Label = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.5.sp)
    val Action = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val Domain = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp)
}

@Composable
fun MuroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MuroColors.Accent,
            onPrimary = MuroColors.Background,
            background = MuroColors.Background,
            onBackground = MuroColors.Text,
            surface = MuroColors.Surface,
            onSurface = MuroColors.Text,
            surfaceContainerHigh = MuroColors.Surface,
            onSurfaceVariant = MuroColors.Muted,
            error = MuroColors.Danger,
        ),
        content = content,
    )
}
