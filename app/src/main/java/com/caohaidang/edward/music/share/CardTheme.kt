package com.caohaidang.edward.music.share

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

data class CardTheme(
    val name: String,
    val top: Int,
    val bottom: Int,
    val text: Int = 0xFFFFFFFF.toInt(),
    val accent: Int = 0xFFFFFFFF.toInt(),

    val useBlur: Boolean = false,

    val solidCard: Boolean = false,
) {
    val subtleText: Int get() = ColorUtils.setAlphaComponent(text, 170)
    val panel: Int get() = ColorUtils.setAlphaComponent(text, 30)

    companion object {
        val Night = CardTheme("night", 0xFF2B2D5B.toInt(), 0xFF0B0B14.toInt(), accent = 0xFFB8A6FF.toInt())
        val Sunset = CardTheme("sunset", 0xFFFF7A59.toInt(), 0xFF5B1B6B.toInt(), accent = 0xFFFFE3A3.toInt())
        val Ocean = CardTheme("ocean", 0xFF1F9BC4.toInt(), 0xFF0A1F33.toInt(), accent = 0xFF9BF3FF.toInt())
        val Forest = CardTheme("forest", 0xFF1DB98A.toInt(), 0xFF083326.toInt(), accent = 0xFFC7FFD8.toInt())
        val Light = CardTheme(
            "light", 0xFFFFFFFF.toInt(), 0xFFE6E3F2.toInt(),
            text = 0xFF14141C.toInt(), accent = 0xFF6B4EFF.toInt()
        )

        val WrappedWeek = CardTheme("wrapped_week", 0xFF11998E.toInt(), 0xFF0B1F3A.toInt(), accent = 0xFFB6FFE4.toInt())
        val WrappedMonth = CardTheme("wrapped_month", 0xFF8E2DE2.toInt(), 0xFF1A0B3A.toInt(), accent = 0xFFFFB6F2.toInt())
        val WrappedYear = CardTheme("wrapped_year", 0xFFFF6A3D.toInt(), 0xFF3A0B1F.toInt(), accent = 0xFFFFE08A.toInt())

        val presets = listOf(Night, Sunset, Ocean, Forest, Light)

        fun fromArtwork(cover: Bitmap?, solidCard: Boolean = false): CardTheme {
            if (cover == null) return Night.copy(solidCard = solidCard)
            val palette = Palette.from(cover).maximumColorCount(16).generate()
            val base = palette.getDarkMutedColor(
                palette.getMutedColor(palette.getDominantColor(0xFF2B2D5B.toInt()))
            )
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(base, hsl)
            val top = ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceAtLeast(0.25f), hsl[2].coerceIn(0.22f, 0.36f)))
            val bottom = ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceAtLeast(0.2f), 0.08f))
            val accent = ColorUtils.HSLToColor(floatArrayOf(hsl[0], 0.9f, 0.8f))
            return CardTheme(
                name = if (solidCard) "artwork_solid" else "artwork",
                top = top, bottom = bottom, accent = accent,
                useBlur = true, solidCard = solidCard,
            )
        }
    }
}
