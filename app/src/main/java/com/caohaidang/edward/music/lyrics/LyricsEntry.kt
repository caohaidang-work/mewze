package com.caohaidang.edward.music.lyrics

/** Một từ (hoặc cụm) có mốc thời gian riêng — có khi lời ở dạng Enhanced LRC `<mm:ss.xx>`. */
data class LyricsWord(
    val time: Long,
    val text: String,
)

data class LyricsEntry(
    val time: Long,
    val text: String,
    /** null nếu nguồn lời chỉ có mốc theo dòng. */
    val words: List<LyricsWord>? = null,
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = time.compareTo(other.time)

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}
