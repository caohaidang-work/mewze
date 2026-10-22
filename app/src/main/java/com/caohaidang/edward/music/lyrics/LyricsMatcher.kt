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

    /** Tên nghệ sĩ đầy đủ (giữ nguyên danh sách), chỉ bỏ "- Topic", VEVO, official. */
    fun cleanArtist(artist: String): String =
        artist.replace(ARTIST_NOISE, "").replace(Regex("""\s+"""), " ").trim().ifEmpty { artist.trim() }

    /** Tiêu đề kiểu YouTube "Nghệ sĩ - Tên bài" -> "Tên bài" (null nếu không có dạng đó). */
    fun titleAfterDash(title: String): String? {
        val parts = title.split(Regex("""\s+[-–—]\s+"""), limit = 2)
        if (parts.size < 2) return null
        return cleanTitle(parts[1]).takeIf { it.isNotBlank() }
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

    /** Độ giống (0..1) giữa hai đoạn chữ sau khi bỏ dấu/viết thường/bỏ ký tự đặc biệt. */
    fun textSimilarity(a: String, b: String): Double = similarity(normalize(a), normalize(b))

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

    // ── Bài có feat / collab và các phiên bản (live, remix...) ────────────────────
    //
    // Vấn đề: cùng một bài, lúc YouTube ghi "Tên bài (feat. B)" + nghệ sĩ "A", lúc ghi "Tên bài" + nghệ sĩ "A, B",
    // lúc kho lời chỉ lưu "A". Bài cũ của A sau này ra bản collab với B cũng cùng tên. Nếu chỉ so "có trùng một
    // nghệ sĩ nào không" thì bản solo và bản collab (lời khác) bị nhầm với nhau.
    // Cách làm: gộp nghệ sĩ ở ô nghệ sĩ + nghệ sĩ feat trong tên bài thành MỘT tập, rồi so theo tập;
    // thêm nhận diện phiên bản (live/remix/acoustic...) để không lẫn bản khác.

    private val FEAT_BRACKET = Regex(
        """[(\[{（【]\s*(?:feat|ft|featuring|with|cùng với|ft\.)\.?\s*([^)\]}）】]+)[)\]}）】]""",
        RegexOption.IGNORE_CASE
    )
    private val FEAT_PLAIN = Regex(
        """\b(?:feat|ft|featuring)\b\.?\s+([^()\[\]{}\-–—]+)""",
        RegexOption.IGNORE_CASE
    )
    private val VERSION_KEYWORDS = listOf(
        "live", "remix", "acoustic", "instrumental", "karaoke", "sped up", "slowed", "reverb",
        "cover", "demo", "lofi", "lo-fi", "unplugged", "extended", "nightcore", "beat"
    ).map { it to Regex("""(?<![\p{L}\p{N}])${Regex.escape(it)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE) }

    /** Nghệ sĩ feat ghi trong tên bài: "Bài (feat. B & C)" -> [B, C]. */
    fun featuredArtists(title: String): List<String> =
        (FEAT_BRACKET.findAll(title) + FEAT_PLAIN.findAll(title))
            .flatMap { it.groupValues[1].split(ARTIST_SPLIT).asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    /** Tập nghệ sĩ đầy đủ của một bài = ô nghệ sĩ + feat trong tên bài (đã chuẩn hoá, bỏ trùng). */
    fun artistSet(artist: String, title: String): List<String> =
        (artistNames(artist) + featuredArtists(title))
            .map(::normalize).filter { it.isNotEmpty() }.distinct()

    /** Các nhãn phiên bản có trong tên bài (live, remix, acoustic...). */
    fun versionTags(title: String): Set<String> =
        VERSION_KEYWORDS.filter { (_, re) -> re.containsMatchIn(title) }.map { it.first.replace("lo-fi", "lofi") }.toSet()

    /** Bản live không được dùng lời của bản studio và ngược lại (remix, acoustic... tương tự). */
    fun versionCompatible(wantedTitle: String, foundTitle: String): Boolean =
        versionTags(wantedTitle) == versionTags(foundTitle)

    /** Jaccard giữa hai tập nghệ sĩ (so mờ: tên giống >= 0.85 coi là một người). 1.0 = trùng hoàn toàn. */
    fun artistSetSimilarity(a: List<String>, b: List<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.5
        val overlap = a.count { x -> b.any { y -> similarity(x, y) >= 0.85 } }
        val union = a.size + b.size - overlap
        return if (union <= 0) 1.0 else overlap.toDouble() / union
    }

    /**
     * Đánh giá một bài tìm được có phải ĐÚNG bài đang phát không. Trả điểm (cao hơn = khớp hơn) hoặc null nếu loại.
     *  - khác phiên bản (live/remix/...) -> loại;
     *  - tên bài + nghệ sĩ phải đạt ngưỡng (nới hơn khi [relaxed]);
     *  - tập nghệ sĩ trùng hoàn toàn -> cho lệch thời lượng 4s; KHÁC tập (vd solo vs collab) -> chỉ 2s,
     *    vì bản collab thường dài/ngắn hơn bản gốc;
     *  - tập nghệ sĩ giống hơn được cộng điểm, để khi có nhiều ứng viên thì chọn đúng bản collab.
     */
    fun evaluate(
        wantedTitle: String,
        wantedArtist: String,
        foundTitle: String,
        foundArtist: String,
        wantedDuration: Int,
        foundDuration: Number?,
        relaxed: Boolean = false,
    ): Double? {
        if (!versionCompatible(wantedTitle, foundTitle)) return null

        val wantedSet = artistSet(wantedArtist, wantedTitle)
        val foundSet = artistSet(foundArtist, foundTitle)
        val setSim = artistSetSimilarity(wantedSet, foundSet)
        val titleSim = titleSimilarity(wantedTitle, foundTitle)
        val artistSim = maxOf(artistSimilarity(wantedArtist, foundArtist), setSim)
        val delta = durationDelta(wantedDuration, foundDuration)

        // Hai tập nghệ sĩ phải CÙNG NHAU (mỗi người bên này đều có bên kia). Khác tập = rất có thể là
        // bản solo vs bản collab (lời khác). Chỉ tha khi nguồn thiếu tên feat NHƯNG thời lượng sát từng giây;
        // không biết thời lượng thì loại (thà không có lời còn hơn sai bài).
        val sameSet = wantedSet.isEmpty() || foundSet.isEmpty() ||
            (wantedSet.all { w -> foundSet.any { similarity(w, it) >= 0.85 } } &&
                foundSet.all { f -> wantedSet.any { similarity(f, it) >= 0.85 } })
        if (!sameSet && (delta == null || delta > 1)) return null

        val tolerance = 4 * (if (relaxed) 2 else 1)
        if (delta != null && delta > tolerance) return null

        val ok = if (relaxed) titleSim >= 0.5 && artistSim >= 0.2 else isMatch(titleSim, artistSim, delta)
        if (!ok) return null
        return score(titleSim, artistSim) + setSim * 0.7 - (delta ?: 2) * 0.1
    }
}
