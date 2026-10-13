package com.caohaidang.edward.music.ui.utils

private val WH_REGEX =
    Regex("""^(https://(?:lh3\.googleusercontent\.com|yt3\.googleusercontent\.com|yt3\.ggpht\.com)/.*?)=w(\d+)-h(\d+).*$""")
private val S_REGEX =
    Regex("""^(https://(?:lh3\.googleusercontent\.com|yt3\.googleusercontent\.com|yt3\.ggpht\.com)/.*?)=s(\d+).*$""")

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    WH_REGEX.matchEntire(this)?.let { m ->
        val base = m.groupValues[1]
        val srcW = m.groupValues[2].toInt().coerceAtLeast(1)
        val srcH = m.groupValues[3].toInt().coerceAtLeast(1)
        val w = width ?: Math.round(height!! * srcW.toFloat() / srcH)
        val h = height ?: Math.round(width!! * srcH.toFloat() / srcW)
        return "$base=w$w-h$h-p-l90-rj"
    }
    S_REGEX.matchEntire(this)?.let { m ->
        return "${m.groupValues[1]}=s${width ?: height}-p-l90-rj"
    }
    return this
}
