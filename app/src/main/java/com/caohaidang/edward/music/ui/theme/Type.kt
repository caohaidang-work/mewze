package com.caohaidang.edward.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.caohaidang.edward.music.R

val AppFontFamily = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

private fun TextStyle.withApp() = copy(fontFamily = AppFontFamily)

private val Base = Typography()

val AppTypography = Typography(
    displayLarge = Base.displayLarge.withApp(),
    displayMedium = Base.displayMedium.withApp(),
    displaySmall = Base.displaySmall.withApp(),
    headlineLarge = Base.headlineLarge.withApp(),
    headlineMedium = Base.headlineMedium.withApp(),
    headlineSmall = Base.headlineSmall.withApp(),
    titleLarge = Base.titleLarge.withApp(),
    titleMedium = Base.titleMedium.withApp(),
    titleSmall = Base.titleSmall.withApp(),
    bodyLarge = Base.bodyLarge.withApp(),
    bodyMedium = Base.bodyMedium.withApp(),
    bodySmall = Base.bodySmall.withApp(),
    labelLarge = Base.labelLarge.withApp(),
    labelMedium = Base.labelMedium.withApp(),
    labelSmall = Base.labelSmall.withApp(),
)
