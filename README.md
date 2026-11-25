# DineFlow Backend – Spring Boot

Bản Java Spring Boot của `dineflow_backend` (Laravel). **Giữ nguyên mọi URL, JSON trả về và quy tắc nghiệp vụ**
của REST API, nên app Flutter chỉ cần trỏ base URL vào server này là chạy, không phải sửa code.

## 1. Chạy server

Yêu cầu: **JDK 17+**, **IntelliJ IDEA** (đã có sẵn Maven), MySQL (XAMPP / Laragon đều được).

1. IntelliJ → *File → Open* → chọn thư mục này (có file `pom.xml`) → đợi tải thư viện xong.
2. Mở `src/main/resources/application.properties`, sửa user/password MySQL nếu cần
   (mặc định `root` / không mật khẩu, database `dineflow` — **tự tạo nếu chưa có**).
3. Chạy `DineflowApplication.java` (nút ▶).

Lần chạy đầu, khi bảng `users` còn trống, server tự tạo bảng và dữ liệu mẫu
(giống `php artisan migrate --seed`). Muốn làm lại từ đầu: xóa database `dineflow` rồi chạy lại.

**Chưa cài MySQL?** Chạy với profile `h2` (database lưu trong file `./data`):
IntelliJ → *Edit Configurations* → *Active profiles*: `h2`. Hoặc dòng lệnh:
```bat
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

| Việc | Địa chỉ |
|---|---|
| API | http://127.0.0.1:8000/api |
| Ảnh món | http://127.0.0.1:8000/storage/foods/... |

App Flutter: Android Emulator dùng `http://10.0.2.2:8000/api`; điện thoại thật dùng `http://<IP máy>:8000/api`
và sửa `app.url` trong `application.properties` thành `http://<IP máy>:8000` để link ảnh / link Excel mở được.

## 2. Tài khoản mẫu (mật khẩu `123456`)

| Vai trò | Email |
|---|---|
| Admin | admin@dineflow.com |
| NV Order | staff@dineflow.com |
| Thu ngân | cashier@dineflow.com |
| Khách hàng | customer@dineflow.com (mã thành viên DF-MB0001) |
| Bị khóa | locked@dineflow.com |

## 3. Dùng chung database với bản Laravel

Tên bảng, tên cột và cách lưu giống hệt Laravel: mật khẩu BCrypt, token đăng nhập kiểu Sanctum
(`personal_access_tokens`), giờ UTC, cột JSON. Trỏ vào database Laravel cũ thì dữ liệu và **token
đang đăng nhập trên app vẫn dùng tiếp được**.

## 4. Laravel → Spring Boot: file nào nằm ở đâu

Kiểu React: *controller* ≈ route handler, *service* ≈ custom hook chứa logic dùng chung,
*entity* ≈ type/interface của dữ liệu, *repository* ≈ hàm gọi API tới database.

| Laravel | Spring Boot (`src/main/java/com/dineflow/`) |
|---|---|
| `routes/api.php` | `@GetMapping` / `@PostMapping`... đặt ngay trên hàm trong `controller/` |
| `app/Http/Controllers/Api/*` | `controller/*` (Favorite + Notification gộp vào `CustomerController`) |
| `app/Http/Controllers/Api/Admin/*` | `controller/admin/*` |
| `app/Models/*` | `entity/*` (bảng) + `repository/*` (truy vấn) |
| Hàm nghiệp vụ trong Model (`occupy`, `cancelBy`, `expireStale`...) | `service/TableService`, `OrderService`, `ReservationService` |
| `app/Services/*` | `service/*` (cùng tên) |
| `app/Http/Resources/*` | `service/Resources.java` (mỗi Resource là một hàm) |
| `app/Enums/*` | `enums/*` |
| `$request->validate([...])` | `Input` (`common/Input.java`) |
| Middleware `auth:sanctum`, `active`, `role:...`, `throttle` | `security/AuthInterceptor` + `@Roles(...)`, `@Public`, `@Throttle` |
| `bootstrap/app.php` (khuôn lỗi JSON) | `common/GlobalExceptionHandler` |
| `config/restaurant.php`, `.env` | `application.properties` |
| `config/loyalty.php` | `config/LoyaltyConfig.java` |
| `database/migrations/*` | Tự sinh từ `entity/*` (`ddl-auto=update`) |
| `database/seeders/DatabaseSeeder.php` | `seed/DataSeeder.java` |
| OpenSpout (xuất Excel) | Apache POI (`service/ReportExporter`) |
| `URL::temporarySignedRoute` | `security/SignedUrlService` |

## 5. Gửi email OTP quên mật khẩu

Mặc định `app.mail.mode=log`: email chỉ in ra console (giống `MAIL_MAILER=log`).
Gửi thật bằng Gmail: đổi thành `app.mail.mode=smtp`, điền `spring.mail.username` và
`spring.mail.password` (Mật khẩu ứng dụng 16 ký tự của Gmail).

## 6. Khác với bản Laravel

- **Trang web `/admin` (Filament) không được chuyển.** Filament là framework giao diện riêng của PHP.
  Mọi chức năng quản trị vẫn có đủ qua API `/api/admin/*` mà app Admin đang dùng.
- `POST /api/admin/foods` nhận `image_url` và lưu vào ảnh món (bản PHP nhận nhưng bỏ qua do thiếu trong `$fillable`).
- Giờ trong nội dung thông báo ("Đặt bàn thành công lúc 19:00...") hiển thị theo giờ Việt Nam
  (bản PHP in theo giờ UTC, lệch 7 tiếng).
- Thông báo lỗi kiểm tra dữ liệu mặc định viết bằng tiếng Việt (Laravel để `APP_LOCALE=en` nên một số câu là tiếng Anh).
