package com.caohaidang.edward.music.lyrics

import com.caohaidang.edward.kugou.KuGou
import java.text.Normalizer
import kotlin.math.abs

object LyricsMatcher {
    private val BRACKETS = Regex("""[(\[{（【「『《〈<][^)\]}）】」』》〉>]*[)\]}）】」』》〉>]""")
    private val FEATURING = Regex("""\s*[-–—]?\s*\b(feat|ft|featuring)\b\.?.*$""", RegexOption.IGNORE_CASE)
    private val VERSION_SUFFIX = Regex(
        """\s+[-–—]\s+.*\b(remaster(ed)?|live|version|ver\.?|edit|mix|mono|stereo|acoustic|ost|instrumental|sped up|slowed)\b.*$""",
        RegexOption.IGNORE_CASE
    )
    private val ARTIST_NOISE = Regex("""\s*[-–—]\s*topic$|vevo$|\bofficial\b""", RegexOption.IGNORE_CASE)
    private val ARTIST_SPLIT = Regex("""\s*(?:,|&|、|/|;|\+|\bfeat\.?\b|\bft\.?\b|\bx\b|\bvà\b|\band\b|\bwith\b)\s*""", RegexOption.IGNORE_CASE)
    private val NOT_ALNUM = Regex("""[^\p{L}\p{N}]+""")
    private val COMBINING = Regex("""[̀-ͯ]""")
    private val HAN = Regex("""[一-鿿]""")
    private val KANA = Regex("""[぀-ヿ]""")

    fun cleanTitle(title: String): String {
        val cleaned = title
            .replace(BRACKETS, " ")
            .replace(VERSION_SUFFIX, "")
            .replace(FEATURING, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return cleaned.ifEmpty { title.trim() }
    }

    fun primaryArtist(artist: String): String =
        artistNames(artist).firstOrNull() ?: artist.trim()

    private fun artistNames(artist: String): List<String> =
        artist.replace(ARTIST_NOISE, "")
            .split(ARTIST_SPLIT)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun normalize(text: String): String {
        var s = text.lowercase().replace('đ', 'd')
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING, "")
        s = Normalizer.normalize(s, Normalizer.Form.NFC)
        if (HAN.containsMatchIn(s) && !KANA.containsMatchIn(s)) s = KuGou.toSimplified(s)
        return s.replace(NOT_ALNUM, "")
    }

    private fun bigrams(s: String): Map<String, Int> {
        val map = HashMap<String, Int>()
        for (i in 0 until s.length - 1) map.merge(s.substring(i, i + 2), 1, Int::plus)
        return map
    }

    private fun dice(a: String, b: String): Double {
        if (a.length < 2 || b.length < 2) return if (a == b) 1.0 else 0.0
        val ba = bigrams(a)
        val bb = bigrams(b)
        var overlap = 0
        ba.forEach { (k, v) -> overlap += minOf(v, bb[k] ?: 0) }
        return 2.0 * overlap / ((a.length - 1) + (b.length - 1))
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val (short, long) = if (a.length <= b.length) a to b else b to a
        if (short.length >= 3 && long.contains(short)) return 0.8
        return dice(a, b)
    }

    fun titleSimilarity(wanted: String, found: String): Double {
        val raw = similarity(normalize(wanted), normalize(found))
        val cleaned = similarity(normalize(cleanTitle(wanted)), normalize(cleanTitle(found)))
        return maxOf(raw, cleaned)
    }

    fun artistSimilarity(wanted: String, found: String): Double {
        val w = artistNames(wanted).map(::normalize).filter { it.isNotEmpty() }
        val f = artistNames(found).map(::normalize).filter { it.isNotEmpty() }
        if (w.isEmpty() || f.isEmpty()) return 0.5
        return w.maxOf { a -> f.maxOf { b -> similarity(a, b) } }
    }

    fun score(titleSim: Double, artistSim: Double): Double = titleSim * 2 + artistSim

    fun isMatch(titleSim: Double, artistSim: Double, durationDelta: Int? = null): Boolean = when {
        titleSim >= 0.9 && (artistSim >= 0.2 || durationDelta == null || durationDelta <= 3) -> true
        titleSim >= 0.6 && artistSim >= 0.4 -> true
        else -> false
    }

    fun isMatch(wantedTitle: String, wantedArtist: String, foundTitle: String, foundArtist: String, durationDelta: Int? = null) =
        isMatch(titleSimilarity(wantedTitle, foundTitle), artistSimilarity(wantedArtist, foundArtist), durationDelta)

    fun durationDelta(wanted: Int, found: Number?): Int? =
        if (wanted <= 0 || found == null) null else abs(found.toDouble() - wanted).toInt()
}
