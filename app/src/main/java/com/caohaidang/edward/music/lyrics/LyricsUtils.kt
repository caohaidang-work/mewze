package com.caohaidang.edward.music.lyrics

import android.text.format.DateUtils
import com.caohaidang.edward.music.ui.component.animateScrollDuration

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\])+)(.+)".toRegex()
    val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()
    private val WORD_TAG_REGEX = "<(\\d\\d):(\\d\\d)\\.(\\d{2,3})>".toRegex()

    fun parseLyrics(lyrics: String): List<LyricsEntry> =
        lyrics.lines()
            .flatMap { line ->
                parseLine(line).orEmpty()
            }.sorted()

    private fun parseLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val rawText = matchResult.groupValues[3]
        val words = parseWords(rawText)
        val text = if (words != null) words.joinToString("") { it.text } else rawText
        val timeMatchResults = TIME_REGEX.findAll(times)

        return timeMatchResults.map { timeMatchResult ->
            val min = timeMatchResult.groupValues[1].toLong()
            val sec = timeMatchResult.groupValues[2].toLong()
            val milString = timeMatchResult.groupValues[3]
            var mil = milString.toLong()
            if (milString.length == 2) {
                mil *= 10
            }
            val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
            LyricsEntry(time, text, words)
        }.toList()
    }

    private fun toMillis(min: String, sec: String, frac: String): Long {
        var mil = frac.toLong()
        if (frac.length == 2) mil *= 10
        return min.toLong() * DateUtils.MINUTE_IN_MILLIS + sec.toLong() * DateUtils.SECOND_IN_MILLIS + mil
    }

    /** Enhanced LRC: `<00:12.34>từ <00:12.80>kế`. Trả null nếu dòng không có thẻ từ. */
    private fun parseWords(raw: String): List<LyricsWord>? {
        val tags = WORD_TAG_REGEX.findAll(raw).toList()
        if (tags.isEmpty()) return null
        val words = ArrayList<LyricsWord>()
        tags.forEachIndexed { i, tag ->
            val end = if (i + 1 < tags.size) tags[i + 1].range.first else raw.length
            val segment = raw.substring(tag.range.last + 1, end)
            if (segment.isNotEmpty()) {
                val (min, sec, frac) = tag.destructured
                words += LyricsWord(toMillis(min, sec, frac), segment)
            }
        }
        // Phần chữ trước thẻ đầu tiên (nếu có) coi như bắt đầu cùng dòng
        return words.takeIf { it.isNotEmpty() }
    }

    /**
     * Số ký tự đã "hát" tới thời điểm [position] (có phần lẻ) cho dòng [entry].
     * - Có mốc từng từ (Enhanced LRC): nội suy trong từ đang hát.
     * - Chỉ có mốc dòng: ước lượng quét đều theo số ký tự trong khoảng
     *   [entry.time, endTime], giới hạn để dòng ngắn không bị kéo dài qua đoạn nhạc đệm.
     */
    fun sungChars(entry: LyricsEntry, endTime: Long, position: Long): Float {
        val n = entry.text.length
        if (n == 0) return 0f
        val words = entry.words
        if (words != null) {
            var offset = 0
            for (i in words.indices) {
                val w = words[i]
                val wEnd = if (i + 1 < words.size) words[i + 1].time else endTime.coerceAtLeast(w.time + 200)
                if (position < w.time) return offset.toFloat()
                if (position < wEnd) {
                    val frac = (position - w.time).toFloat() / (wEnd - w.time).coerceAtLeast(1)
                    return offset + w.text.length * frac
                }
                offset += w.text.length
            }
            return n.toFloat()
        }
        val gap = (endTime - entry.time).coerceAtLeast(1)
        val duration = minOf(gap, maxOf(1500L, n * 280L)).toFloat()
        // Hát xong chút trước khi dòng sau bắt đầu
        val progress = ((position - entry.time) / (duration * 0.92f)).coerceIn(0f, 1f)
        return n * progress
    }

    fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int {
        for (index in lines.indices) {
            if (lines[index].time >= position + animateScrollDuration) {
                return index - 1
            }
        }
        return lines.lastIndex
    }
}
