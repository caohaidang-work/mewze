# Sửa đồng bộ Spotify / YouTube Music

## Nguyên nhân bài "Đã thích" lúc hiện lúc mất
`LibrarySyncManager.syncLikedSongs` coi bài vừa đẩy lên YouTube là "đã đồng bộ" ngay. YouTube hiển thị
lượt thích mới chậm, hoặc trả danh sách bị cắt giữa chừng (lỗi phân trang bị nuốt bằng `?: break`).
Khi đó bài nằm trong snapshot nhưng không có trong danh sách từ xa nên bị bỏ thích trên máy; lần sau
danh sách đầy đủ thì bài lại hiện ra.

Nguyên nhân gốc của việc danh sách bị cắt: YouTube Music có lúc trả "trang tiếp theo" ở dạng mới
(`onResponseReceivedActions.appendContinuationItemsAction`, token nằm ở `continuationItemRenderer` cuối danh sách)
mà code cũ chỉ đọc dạng cũ (`continuationContents`). Danh sách thích dừng sau trang đầu, các bài phía sau bị coi là đã bị xoá.

## Đã sửa
- `innertube`: đọc được cả hai dạng phân trang (`pages/ContinuationChunks.kt`) cho bài thích, playlist, album, nghệ sĩ.
  Trang không đọc được sẽ báo lỗi (và được thử lại) thay vì âm thầm cắt cụt.
- Bài vừa đẩy lên chỉ được tính là đã đồng bộ khi đã thấy trong danh sách YouTube (`pendingLikes`).
- Chỉ bỏ thích/xoá trên máy khi mục đó vắng mặt ở ≥2 lần đồng bộ liên tiếp (≥4 nếu mất hàng loạt)
  (`missing`), áp dụng cho bài thích, playlist, album, nghệ sĩ.
- Phát hiện danh sách bị cắt (so với số bài ghi ở đầu playlist) → tải lại, không dùng bản thiếu.
- `YouTube.likedPlaylists/libraryAlbums/libraryArtists` không còn âm thầm dừng ở trang lỗi.
- Spotify: tải Bài hát đã thích kiểm tra đủ số lượng (tải lại tối đa 3 lần), bỏ trùng; retry khi rớt mạng/5xx;
  bài tìm trên YouTube Music bị lỗi mạng được tìm lại thêm 2 lượt trước khi lưu; nhập xong thì tự đồng bộ lên YouTube.
- `SpotifyAuth`: chỉ đăng xuất khi token bị thu hồi thật (`invalid_grant`), không phải mọi lỗi 400.

## Tối ưu YouTube Music
- Timeout kết nối/đọc/toàn request cho HTTP client (trước đây dễ treo hoặc đứt khi tải thư viện lớn).
- Retry có backoff + jitter cho từng request và từng bước; phân biệt lỗi tạm thời và lỗi 4xx.
- Offline thì bỏ qua êm (không tính là lỗi, không backoff); mất mạng giữa chừng thì bỏ các bước còn lại.
- Tải song song (3 playlist cùng lúc), đẩy like có giãn nhịp và dừng khi YouTube liên tục từ chối.
- Backlog like (vd. sau khi nhập Spotify) được đẩy liên tục tới 250 bài/lượt thay vì 100 bài/5 phút.
