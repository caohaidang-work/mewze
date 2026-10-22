package com.caohaidang.edward.music.lyrics

import android.content.Context
import com.caohaidang.edward.kugou.KuGou

/**
 * KuGou: lời đồng bộ theo dòng, mạnh với nhạc Việt/Hoa/Hàn/Nhật.
 *
 * Chỉ nhận kết quả khi ĐÚNG bài:
 *  - tên bài + nghệ sĩ mà KuGou trả về phải khớp (LyricsMatcher), không chỉ khớp từ khoá tìm kiếm;
 *  - thời lượng lệch tối đa 4 giây (kiểm trong KuGou.getLyricsCandidate).
 * Không đạt -> báo lỗi để LyricsHelper chuyển sang nguồn khác, không bao giờ hiện lời "gần giống".
 */
object KuGouLyricsProvider : LyricsProvider {
    override val name = "KuGou"

    override fun isEnabled(context: Context): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> {
        return KuGou.getLyrics(title, artist, duration) { song, singer ->
            // KuGou không trả thời lượng ở bước này (thời lượng đã được kiểm trong KuGou.getLyricsCandidate).
            LyricsMatcher.evaluate(title, artist, song, singer, 0, null) != null &&
                LyricsMatcher.titleSimilarity(title, song) >= 0.8
        }
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        KuGou.getAllLyrics(title, artist, duration, callback)
    }
}
