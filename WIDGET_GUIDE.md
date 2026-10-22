# Widget "CD Player" (Win98) cho Mewze

Widget màn hình chính 4×2, giao diện Windows 98 giống mẫu: thanh tiêu đề xanh, ảnh bìa đen trắng,
ô **Artist / Track**, thanh trượt tiến độ và 5 nút.

| Nút | Chức năng |
|---|---|
| ◄◄ | Bài trước |
| ►► | Bài tiếp theo |
| ► / ❚❚ | Phát / tạm dừng (đổi biểu tượng theo trạng thái) |
| ■ | Dừng (tạm dừng + về đầu bài) |
| ● | Thích bài hát (chấm đỏ khi đã thích) |
| Thanh tiêu đề, ảnh bìa, ô chữ | Mở app |

## 1. Các file đã thêm / sửa

**Thêm mới** (`app/src/main/`):
- `java/.../widget/CdPlayerWidgetProvider.kt` – nhận cú bấm nút, gửi lệnh tới MusicService qua MediaController
- `java/.../widget/CdPlayerWidgetRenderer.kt` – dựng RemoteViews, vẽ thanh trượt kiểu Win98
- `java/.../widget/CdPlayerWidgetUpdater.kt` – chạy trong service, theo dõi player và cập nhật widget
- `java/.../widget/CdWidgetState.kt` – trạng thái + lưu bài cuối để widget không trống sau khi khởi động lại máy
- `res/layout/widget_cd_player.xml` – giao diện widget
- `res/xml/cd_player_widget_info.xml` – khai báo kích thước widget
- `res/drawable/cdw_*.xml` – viền nổi/lõm Win98, biểu tượng nút, đĩa CD mặc định
- `res/drawable-nodpi/cdw_preview.png` – ảnh xem trước trong bộ chọn widget
- `res/values/cd_widget.xml` – chuỗi chữ

**Sửa** (chỉ vài dòng):
- `AndroidManifest.xml` – thêm `<receiver .../>` cho widget
- `playback/MusicService.kt` – khởi tạo `CdPlayerWidgetUpdater` ở `onCreate`, gọi `stop()` ở `onDestroy`
  (tìm chú thích `Home-screen "CD Player" widget`)

## 2. Build và cài

1. Mở thư mục `mewze` bằng Android Studio, đợi Gradle sync.
2. Chạy `./gradlew assembleDebug` (hoặc nút Run) rồi cài lên máy.
3. Mở app, phát một bài hát.
4. Về màn hình chính → nhấn giữ chỗ trống → **Widgets** → **Mewze** → kéo **CD Player** ra.

## 3. Tuỳ chỉnh nhanh

- **Ảnh bìa màu thay vì đen trắng**: `CdPlayerWidgetUpdater.GRAYSCALE_ART = false`.
- **Màu thanh tiêu đề**: `res/drawable/cdw_titlebar.xml` (`startColor` / `endColor`).
- **Tần suất cập nhật thanh trượt**: `TICK_MS` trong `CdPlayerWidgetUpdater` (mặc định 2 giây, chỉ chạy khi đang phát và màn hình sáng).
- **Đổi chức năng nút ●** (hiện là Thích): sửa `ACTION_LIKE` trong `CdPlayerWidgetProvider`.
  Ví dụ đổi thành bật/tắt shuffle bằng `MediaSessionConstants.CommandToggleShuffle`.
- **Kích thước ảnh bìa**: `android:layout_width/height` của `cdw_art_frame` (và hằng số `SLIDER_OUTER_DP` trong Renderer nếu đổi).
- **Font**: hiện dùng `monospace` đậm của hệ thống. Muốn pixel font thật, bỏ file `.ttf` vào `res/font/`
  rồi đổi `android:fontFamily` của `cdw_artist` / `cdw_track`.

## 4. Cách hoạt động

```
MusicService ── Player events ──► CdPlayerWidgetUpdater ──► CdPlayerWidgetRenderer ──► AppWidgetManager
      ▲                                                                                   │
      └── MediaController ◄── CdPlayerWidgetProvider ◄── PendingIntent (bấm nút) ◄────────┘
```

Widget dùng `RemoteViews` thuần (không Compose/Glance), nên không thêm thư viện nào và chạy từ Android 7 (minSdk 24).
RemoteViews không có SeekBar, vì vậy thanh trượt là một ảnh bitmap được vẽ lại theo vị trí phát.

## 5. Giới hạn đã biết

- Bấm lên thanh trượt **không tua** được (RemoteViews không đọc được toạ độ chạm).
- Nếu app bị tắt hẳn và hàng đợi chưa khôi phục kịp, nút ► sẽ mở app thay vì phát.
- Ba nút `_ □ ×` trên thanh tiêu đề chỉ để trang trí (bấm vào sẽ mở app).
- Mình chưa build được dự án trong môi trường này (không có Android SDK), nên hãy sync + build thử và báo lỗi nếu có.

---

# Widget "iPod" (4×2)

Máy nghe nhạc iPod màu bạc: màn hình viền đen bên trái (ảnh bìa, tên bài, nghệ sĩ, thời gian đã phát,
thanh tiến độ, thời lượng; nền màn hình lấy màu từ ảnh bìa) và vòng click wheel trắng bên phải.

| Vùng bấm | Chức năng |
|---|---|
| Nửa trái / phải của vòng | Bài trước / bài tiếp theo |
| Phía dưới vòng (▶❚❚) | Phát / tạm dừng |
| Nút tròn giữa, chữ MUSIC, phần còn lại | Mở app |

Các file: `widget/IpodWidgetProvider.kt`, `widget/IpodWidgetRenderer.kt`, `res/layout/widget_ipod.xml`,
`res/xml/ipod_widget_info.xml`, `res/values/ipod_widget.xml`, `res/drawable-nodpi/ip_preview.png`.
Đã đăng ký `<receiver .../>` trong `AndroidManifest.xml` và nối vào `CdPlayerWidgetUpdater`
(cập nhật theo bài hát, trạng thái phát và thanh tiến độ như hai widget còn lại).

Thêm widget: nhấn giữ màn hình chính → **Widgets** → **Mewze** → kéo **Mewze iPod** ra.
Widget co giãn theo kích thước (tối thiểu 180×90 dp). Thanh tiến độ không tua được khi chạm (giới hạn của RemoteViews).
