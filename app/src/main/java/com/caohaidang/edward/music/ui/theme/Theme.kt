package com.caohaidang.edward.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.google.material.color.dynamiccolor.DynamicScheme
import com.google.material.color.hct.Hct
import com.google.material.color.scheme.SchemeTonalSpot
import com.google.material.color.score.Score

val DefaultThemeColor = Color(0xFF4285F4)

val AppleAccent = Color(0xFFD99B26)

private val AppleDarkScheme = darkColorScheme(
    primary = AppleAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1219),
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF8E8E93),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2C2C2E),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceTint = AppleAccent,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    inversePrimary = AppleAccent,
    error = Color(0xFFFF453A),
    onError = Color.White,
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF38383A),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2C2C2E),
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF242426),
    surfaceContainerHighest = Color(0xFF2C2C2E),
)

private val AppleLightScheme = lightColorScheme(
    primary = Color(0xFFD99B26),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E6),
    onPrimaryContainer = Color(0xFF410008),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF8E8E93),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E5EA),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceTint = Color(0xFFFA243C),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color.White,
    inversePrimary = AppleAccent,
    error = Color(0xFFFF3B30),
    onError = Color.White,
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFF2F2F7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFEBEBF0),
    surfaceContainerHighest = Color(0xFFE5E5EA),
)

@Composable
fun InnerTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") pureBlack: Boolean = false,
    @Suppress("UNUSED_PARAMETER") themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AppleDarkScheme else AppleLightScheme,
        typography = AppTypography,
        content = content
    )
}

@Composable
fun SongTheme(
    darkTheme: Boolean,
    themeColor: Color,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(darkTheme, themeColor) {
        SchemeTonalSpot(Hct.fromInt(themeColor.toArgb()), darkTheme, 0.0).toColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun DynamicScheme.toColorScheme() = ColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    inversePrimary = Color(inversePrimary),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onBackground),
    surface = Color(surface),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    surfaceTint = Color(primary),
    inverseSurface = Color(inverseSurface),
    inverseOnSurface = Color(inverseOnSurface),
    error = Color(error),
    onError = Color(onError),
    errorContainer = Color(errorContainer),
    onErrorContainer = Color(onErrorContainer),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    scrim = Color(scrim),
    surfaceBright = Color(surfaceBright),
    surfaceDim = Color(surfaceDim),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainerLowest = Color(surfaceContainerLowest)
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
