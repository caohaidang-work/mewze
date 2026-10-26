# Phát hành APK mà người dùng cập nhật không mất dữ liệu

Android chỉ cho cài APK mới **đè lên** bản cũ khi hai bản được ký bằng **cùng một khóa** và `versionCode`
của bản mới không nhỏ hơn. Nếu không đè được, người dùng phải gỡ app rồi cài lại, và gỡ app thì xóa toàn bộ
dữ liệu (lịch sử nghe, bài thích, playlist...).

## 1. Một khóa duy nhất, dùng mãi mãi

- **Nếu bạn đã từng gửi bản release cho bạn bè** (ví dụ bằng *Build → Generate Signed App Bundle / APK*),
  hãy tìm lại đúng file keystore đã dùng và dùng lại nó. **Đừng tạo khóa mới**, nếu không mọi người phải
  gỡ app một lần nữa.
- Nếu chưa có: *Build → Generate Signed App Bundle / APK → APK → Create new...* (đặt alias, nhớ hai mật khẩu),
  hoặc dùng dòng lệnh:

  ```bash
  keytool -genkeypair -v -keystore mewze.keystore -alias mewze \
    -keyalg RSA -keysize 2048 -validity 36500
  ```

- **Sao lưu file keystore và mật khẩu ở ít nhất hai nơi** (trình quản lý mật khẩu, ổ ngoài...). Mất khóa là
  không bao giờ cập nhật đè được nữa. Không commit khóa lên GitHub (repo của bạn là public).

## 2. Cho Gradle biết khóa ở đâu (làm một lần)

Tạo file `keystore.properties` ở thư mục gốc dự án (file này đã nằm trong `.gitignore`):

```properties
storeFile=/duong/dan/toi/mewze.keystore
storePassword=...
keyAlias=mewze
keyPassword=...
```

Từ đó build release ra APK đã ký bằng một lệnh, không cần mở wizard:

```bash
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

`versionCode` của bản release tự tăng theo thời gian build, nên không cần nhớ tăng số.
Hãy tự tăng `versionName` (hiển thị cho người dùng) khi muốn.

## 3. Release và debug là hai app khác nhau

Bản debug có đuôi `.debug` trong tên gói, nên nó và bản release **không đè lên nhau và không chia sẻ dữ liệu**.
Hãy chọn một loại để gửi cho bạn bè và giữ nguyên. Ai đang dùng bản debug mà chuyển sang release sẽ phải
sao lưu trong app cũ, cài bản mới, rồi khôi phục (một lần).

## 4. Trước mỗi lần gửi bản mới

1. Nếu bạn đổi cấu trúc CSDL (thêm/sửa cột, bảng): tăng `version` trong `MusicDatabase.kt`, viết migration,
   và **thử trên một bản sao lưu dữ liệu thật** trước khi gửi. Một migration lỗi làm app của mọi người
   crash khi mở. Repo hiện không dùng `fallbackToDestructiveMigration`, đây là điều tốt, đừng thêm nó.
2. Build `assembleRelease`, cài đè lên bản trước trên máy bạn và kiểm tra dữ liệu còn nguyên.
3. Kiểm tra chữ ký giống bản trước:

   ```bash
   $ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app-release.apk | grep SHA-256
   ```

   Dòng SHA-256 phải giống hệt giữa các lần phát hành.
