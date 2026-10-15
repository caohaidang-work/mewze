# Thay logo — 4 file cần chú ý

Ghi đè 4 file PNG (nền trong suốt) rồi build lại. Tất cả nằm trong `app/src/main/res/drawable-nodpi/`:

| # | File | Dùng cho |
|---|------|----------|
| 1 | `logo_app.png` (vuông, khuyến nghị 1024×1024) | Icon ứng dụng (kể cả icon themed/monochrome) và phần "Giới thiệu" trong Cài đặt |
| 2 | `logo_wordmark.png` (chữ "mewze", ngang, cắt sát viền) | Màn hình Welcome/đăng nhập (`ui/screens/WelcomeScreen.kt`) |
| 3 | `logo_share.png` (logo app đã tách nền, trong suốt hoàn toàn) | Nhận diện thương hiệu trên thẻ chia sẻ story (`share/ShareCardRenderer.kt`) |
| 4 | `logo_notification.png` (96×96, **trắng đơn sắc trên nền trong suốt**) | Huy hiệu nhỏ trên thanh thông báo / trình phát nhạc ở màn hình khoá (`playback/MusicService.kt`) |

Lưu ý file số 4: Android chỉ dùng độ trong suốt của ảnh và tô lại thành một màu, nên ảnh nhiều màu sẽ thành khối đặc. Hãy dùng hình đơn giản, màu trắng.

Icon cho máy Android cũ (< 8) và icon Play Store: `pip install pillow && python3 tools/generate_launcher_icons.py`
(tạo từ `logo_app.png`).
