# Smart Room IoT Dashboard

React + Vite + TypeScript dashboard, dùng dữ liệu thật từ Spring Boot. Mặc định chọn `smartroom-01`. Giao diện và giờ hiển thị bằng tiếng Việt, responsive desktop/mobile.

## Chạy local

Yêu cầu Node.js 22.12+ (đã kiểm tra với Node 24.20.0).

```powershell
cd D:\iot-projects\SmartRoomIoT\frontend-react
npm ci
Copy-Item .env.example .env
npm run dev
```

Mở **http://localhost:5173**. Nếu `.env` đã có cấu hình riêng, giữ file hiện có thay vì copy đè. Vite dùng `strictPort` để không âm thầm chuyển sang port khác với origin CORS backend. Dừng terminal bằng Ctrl+C.

```ini
VITE_API_BASE_URL=http://localhost:8080
```

API URL được lấy duy nhất tại `src/lib/api.ts`, không hard-code localhost trong component. Cấu hình là base URL của backend, không thêm `/api/devices`. Không đưa secret vào biến `VITE_*` vì Vite đưa các giá trị này vào JavaScript công khai. `.env` được ignore, `.env.example` chỉ có URL mẫu không chứa secret.

```powershell
npm run build
npm run preview
```

`build` chạy TypeScript strict check và tạo `dist/`. `preview` phục vụ bản production tại port 5173, cần dừng dev server trước. `npm run typecheck` chỉ kiểm tra TypeScript.

## Chức năng

- ONLINE/OFFLINE và lastSeen; nhiệt độ, độ ẩm, fan/light lấy từ API thật.
- Điều khiển Fan/Light ON/OFF; khóa cả bốn nút trong lúc gửi và refresh kết quả, chống click gửi trùng. Thiết bị OFFLINE hoặc API state/device lỗi thì khóa điều khiển.
- Thông báo thành công/thất bại và cập nhật lại state/history sau POST. Không tự gán trạng thái ON khi chỉ mới nhận SENT.
- Hai biểu đồ SVG riêng thang đo nhiệt độ/độ ẩm, tối đa 100 mẫu, thời gian tăng dần. Hover hoặc kéo slider/bàn phím để xem mẫu; giá trị null giữ khoảng trống, không nội suy dữ liệu giả.
- Lịch sử command, mới nhất trước; ID, actuator/action, status, createdAt, executedAt. `executedAt=null` hiển thị dấu `—`.
- Alerts phân biệt unresolved/resolved và bộ lọc. Không có endpoint resolve thủ công nên UI chỉ hiển thị trạng thái từ backend.
- Poll các API song song mỗi khoảng 3 giây, không reload trang và không chồng request. Hủy request cũ khi đổi device, unmount hoặc force refresh; phản hồi cũ không ghi đè dữ liệu mới. Request có timeout 8 giây.
- Tạm ngưng poll khi tab bị ẩn, refresh ngay khi quay lại. Tài nguyên API nào lỗi thì giữ dữ liệu cũ của tài nguyên đó và hiển thị cảnh báo; các phần thành công tiếp tục hoạt động.
- Loading skeleton, error banner/notice, empty states; hỗ trợ keyboard, focus visible, reduced motion, bảng cuộn riêng trên mobile.
- Font Be Vietnam Pro đóng gói local qua Fontsource, không gọi Google Fonts.

## Contract đọc từ backend

Đối chiếu trực tiếp `DeviceController.java`, `DeviceQueryService.java`, `BaseEntity.java`, các entity Device/Telemetry/Command/Alert, DTO CommandRequest/DeviceState và GlobalExceptionHandler.

| API | Response |
|---|---|
| GET `/api/devices` | `Device[]` |
| GET `/api/devices/{deviceId}` | `Device` |
| GET `/api/devices/{deviceId}/telemetry/latest` | `Telemetry`; 404 nếu chưa có mẫu |
| GET `/api/devices/{deviceId}/telemetry?limit=100` | `Telemetry[]`, mới nhất trước |
| GET `/api/devices/{deviceId}/commands` | `Command[]`, mới nhất trước |
| POST `/api/devices/{deviceId}/commands` | `Command`, 201 SENT hoặc 503 FAILED |
| GET `/api/devices/{deviceId}/state` | `DeviceState` |
| GET `/api/devices/{deviceId}/alerts` | `Alert[]` |

POST gửi đúng `{ "device": "fan", "action": "ON" }`, trong đó `device=fan|light`, `action=ON|OFF`. API lỗi thông thường trả ApiError `{ timestamp, status, error, message, path }`; riêng publish thất bại trả Command `status=FAILED` với HTTP 503. Frontend xử lý cả hai dạng.

JSON response dùng **camelCase**, không dùng tên cột SQL snake_case. Kiểu đầy đủ tại `src/types.ts`. Backend dùng UTC cho LocalDateTime nhưng không ghi suffix timezone trong JSON; `src/lib/time.ts` thêm UTC trước khi format sang **Asia/Ho_Chi_Minh (UTC+7)**. Giá trị null được hiển thị rõ, không thay bằng 0 hoặc OFF.

`SENT` xác nhận broker đã nhận lệnh. Frontend xem `/state` và telemetry để hiển thị trạng thái thiết bị, không tự điền executedAt hay khẳng định cơ cấu vật lý đã chuyển động.

## Cấu trúc file

```text
frontend-react/
├── .env.example           # URL mẫu, không secret
├── .env                   # URL local, ignored
├── .gitignore
├── package.json / package-lock.json
├── index.html
├── tsconfig*.json
├── vite.config.ts
├── vercel.json            # SPA fallback
├── public/favicon.svg
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── styles.css
│   ├── types.ts / vite-env.d.ts
│   ├── lib/api.ts / time.ts
│   ├── hooks/useDashboard.ts
│   └── components/
│       ├── Icon.tsx / Status.tsx
│       ├── Controls.tsx
│       ├── TelemetryChart.tsx
│       ├── CommandHistory.tsx
│       └── AlertsPanel.tsx
└── dist/                  # Generated production output, ignored
```

## Chuẩn bị cho Vercel

- Root Directory: `frontend-react` nếu repository root là SmartRoomIoT.
- Framework: Vite. Build: `npm run build`. Output: `dist`.
- Đặt `VITE_API_BASE_URL` thành URL backend production rồi build lại. Không sửa component. Biến môi trường Vite được chèn lúc build, không phải runtime.
- `vercel.json` rewrite mọi route SPA về `/index.html`, giữ navigation/deep-link khi refresh. Dashboard dùng các section anchors; path SPA như `/dashboard` cũng phục vụ cùng dashboard.
- API production cần URL HTTPS truy cập được từ trình duyệt người dùng. `localhost` của người xem không phải máy chạy backend của bạn.
- Backend hiện cho phép CORS origin `http://localhost:5173`. Khi triển khai thực tế, cấu hình biến **CORS_ORIGIN** đã có sẵn của Spring Boot thành origin frontend production và restart backend. Đây là cấu hình vận hành backend, không cần đổi source/schema. Hiện tại không thay cấu hình backend và chưa deploy.

Tham khảo: [Vite environment variables](https://vite.dev/guide/env-and-mode), [Vercel SPA rewrites](https://vercel.com/kb/guide/why-is-my-deployed-project-giving-404).

## Kiểm tra đã thực hiện

- `npm run build`: PASS, TypeScript và production bundle.
- 7 GET endpoint với Spring Boot local: HTTP 200; telemetry history tối đa 100 record.
- POST trực tiếp từ dashboard: Fan ON/OFF và Light ON/OFF đều thành công, command ID **5, 6, 7, 8**, status SENT. Quan sát card/state cập nhật ON/OFF; kết thúc fan/light OFF.
- CORS preflight POST từ `http://localhost:5173`: HTTP 200, origin được phép.
- Kiểm tra browser loading ban đầu, error 404 với device không tồn tại, chuyển lại device thật, alerts empty/filter, chart bằng bàn phím, desktop/mobile, poll và lastSeen thay đổi.
- Không tạo telemetry giả; không ghi trực tiếp database; không sửa backend/schema/firmware/EMQX; không push GitHub hoặc deploy Vercel.

Browser dùng API thật trong các kiểm tra trên; chưa tạo dữ liệu alert để ép trạng thái resolved/unresolved vì không thay dữ liệu production.
