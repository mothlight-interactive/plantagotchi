package dev.mothlight.plantagotchi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.pixelmix
import dev.mothlight.plantagotchi.resources.pixelmix_bold
import org.jetbrains.compose.resources.Font

private val NightColors = darkColorScheme(
    primary = PlantagotchiColors.NightSky,
    onPrimary = Color.White,
    secondary = PlantagotchiColors.AmethystSmoke,
    onSecondary = Color.Black,
    background = PlantagotchiColors.NightSky,
    onBackground = Color.White,
    surface = PlantagotchiColors.NightSky,
    onSurface = Color.White,
    error = PlantagotchiColors.Brown,
    onError = Color.White,
)

private val DayColors = lightColorScheme(
    primary = PlantagotchiColors.LightSky,
    onPrimary = Color.White,
    secondary = PlantagotchiColors.AmethystSmoke,
    onSecondary = Color.Black,
    background = PlantagotchiColors.DaySky,
    onBackground = Color.Black,
    surface = PlantagotchiColors.PaleTurquoise,
    onSurface = Color.Black,
    error = PlantagotchiColors.Brown,
    onError = Color.White,
)

private val PlantagotchiShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(0.dp),
)

@Composable
private fun pixelTypography(): Typography {
    val pixelmix = FontFamily(
        Font(Res.font.pixelmix, FontWeight.Medium),
        Font(Res.font.pixelmix_bold, FontWeight.Bold),
    )
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Medium, spacing: Double = 0.0) =
        TextStyle(fontFamily = pixelmix, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

    return Typography(
        displayLarge = style(57, 64, FontWeight.Bold),
        displayMedium = style(45, 52, FontWeight.Bold),
        displaySmall = style(36, 44, FontWeight.Bold),
        headlineLarge = style(32, 40),
        headlineMedium = style(28, 36),
        headlineSmall = style(24, 32),
        titleLarge = style(22, 28, FontWeight.Bold),
        titleMedium = style(16, 24, FontWeight.Bold),
        titleSmall = style(14, 20, FontWeight.Bold, 0.25),
        bodyLarge = style(16, 24, spacing = 0.25),
        bodyMedium = style(14, 20, spacing = 0.25),
        bodySmall = style(12, 16, spacing = 0.4),
        labelLarge = style(14, 20, spacing = 0.25),
        labelMedium = style(12, 16, spacing = 0.25),
        labelSmall = style(11, 16, spacing = 0.25),
    )
}

@Composable
fun PlantagotchiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NightColors else DayColors,
        typography = pixelTypography(),
        shapes = PlantagotchiShapes,
        content = content,
    )
}
