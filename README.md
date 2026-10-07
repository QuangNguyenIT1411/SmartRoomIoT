# SmartRoom IoT

Monorepo cho hệ thống phòng thông minh: ESP32 → MQTT EMQX → Spring Boot → PostgreSQL → React dashboard. Device dùng khi demo: `smartroom-01`.

## Cấu trúc

| Thư mục/file | Nội dung |
|---|---|
| `backend-springboot/` | Java 17, Spring Boot, REST API, MQTT ingestion và command |
| `frontend-react/` | React, Vite, TypeScript, dashboard responsive |
| `database/init.sql` | Script khởi tạo schema cho database mới |
| `docker-compose.yml` | PostgreSQL 17, port local 5433, named volume |

Tài liệu API và hướng dẫn chi tiết: [backend](backend-springboot/README.md), [frontend](frontend-react/README.md). Firmware hiện chưa có trong thư mục này; không tạo hoặc thay đổi firmware runtime. Tài liệu môn học `BaiTap_IoT_v31.docx` được giữ local và ignore vì chứa ví dụ credential broker.

## Cấu hình local

Yêu cầu Java 17, Node.js 22.12+ và Docker khi dùng PostgreSQL trong container. Maven Wrapper có sẵn trong backend.

Từ root repository, nếu chưa có `.env`:

```powershell
Copy-Item .env.example .env
notepad .env
```

Điền `DB_PASSWORD` bằng mật khẩu **đang dùng** cho PostgreSQL. Docker Compose và backend cùng đọc giá trị này. Không đổi mật khẩu của database hiện có: đặt biến môi trường không tự cập nhật mật khẩu đã lưu trong volume. Backend đọc `.env` từ root hoặc thư mục cha khi chạy trong `backend-springboot`; biến môi trường hệ điều hành có độ ưu tiên cao hơn.

Backend mặc định dùng PostgreSQL `localhost:5433`, database `smartroom_iot`, user `smartroom`, MQTT `tcp://192.168.130.174:1883`, CORS `http://localhost:5173`. Có thể đổi bằng `DB_URL`, `DB_USERNAME`, `MQTT_URI`, `CORS_ORIGIN` trong môi trường hoặc root `.env`. Không ghi credential vào source hay URL MQTT.

Không cần tạo lại database/EMQX đang chạy. Với máy mới, có thể dùng `docker compose up -d postgres`; `database/init.sql` chỉ chạy khi volume database còn trống. Không chạy `docker compose down -v` trên dữ liệu cần giữ.

## Chạy và kiểm tra

Backend, từ `backend-springboot/`:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Frontend, từ `frontend-react/`, giữ `.env` nếu đã có:

```powershell
npm ci
Copy-Item .env.example .env
npm run typecheck
npm run build
npm run dev
```

Backend: `http://localhost:8080`. Dashboard: `http://localhost:5173`. Không chạy thêm instance backend nếu instance hiện tại còn hoạt động.

Backend tests dùng mocks, không ghi telemetry giả hay thay đổi PostgreSQL/EMQX. JPA dùng `ddl-auto=validate`, không tự thay schema. Build output và dependency local không thuộc Git.

## Git và cấu hình nhạy cảm

Root `.gitignore` loại `.env` và các biến thể, private key/keystore, credential directory, IDE metadata, log, database dump, `node_modules/`, Maven `target/`, Vite `dist/`, TypeScript build cache và `.vercel/`. `.env.example`, Maven Wrapper và npm lockfile được commit. File example chỉ chứa placeholder hoặc URL công khai. Không dùng `git add -f` để đưa cấu hình local vào Git.

Frontend `VITE_*` được nhúng vào bundle công khai; chỉ đặt `VITE_API_BASE_URL`, không đặt secret. Vercel dùng Root Directory `frontend-react`, Build Command `npm run build`, Output Directory `dist`; đổi `VITE_API_BASE_URL` thành backend HTTPS và cấu hình `CORS_ORIGIN` tương ứng trước khi triển khai. SPA fallback đã có trong `frontend-react/vercel.json`.
