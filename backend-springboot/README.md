# Smart Room IoT — Spring Boot backend

Backend core cho PostgreSQL, MQTT EMQX và REST API. Java 17, Spring Boot 3.5.16, Maven Wrapper 3.9.16.

## Chạy trên Windows PowerShell

Trước khi chạy, tạo `.env` tại root monorepo từ `.env.example` và điền `DB_PASSWORD` bằng mật khẩu PostgreSQL local hiện có. Backend đọc file này khi chạy từ root hoặc `backend-springboot`; biến môi trường hệ điều hành có độ ưu tiên cao hơn. Không commit `.env`. Không cần đổi mật khẩu hay tạo lại database.

```powershell
cd D:\iot-projects\SmartRoomIoT\backend-springboot
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Hoặc chạy JAR đã build:

```powershell
java -jar target\smartroom-backend-0.0.1-SNAPSHOT.jar
```

Dừng phiên chạy trong terminal bằng `Ctrl+C`. Chỉ chạy một instance với client ID mặc định `smartroom-backend`.

Maven Wrapper tự tải Maven ở lần chạy đầu; cần Internet để tải dependencies. Không cần cài Maven toàn máy.

## Cấu hình

| Biến môi trường | Giá trị mặc định |
|---|---|
| `SERVER_PORT` | `8080` |
| `DB_URL` | `jdbc:postgresql://localhost:5433/smartroom_iot` |
| `DB_USERNAME` | `smartroom` |
| `DB_PASSWORD` | Bắt buộc cấu hình; không có mật khẩu mặc định trong source |
| `MQTT_BROKER_URL` | `MQTT_URI` cũ, hoặc `tcp://192.168.130.174:1883` |
| `MQTT_CLIENT_ID` | `smartroom-backend` |
| `MQTT_USERNAME` | Rỗng: local anonymous |
| `MQTT_PASSWORD` | Rỗng: không gửi password |
| `MQTT_TLS_ENABLED` | `false`; cloud đặt `true` và URL `ssl://` |
| `MQTT_CA_PATH` | Rỗng: JVM trust store; có thể dùng đường dẫn, `file:` URI hoặc `classpath:` resource |
| `CORS_ORIGIN` | `http://localhost:5173` |

Ví dụ đổi port: `$env:SERVER_PORT = '8081'` rồi chạy lại backend.

`backend-springboot/.env.example` liệt kê cấu hình đầy đủ. Giữ root `.env` đang hoạt động; nếu tạo thêm backend `.env`, giá trị trong file này ghi đè root khi chạy từ thư mục backend, kể cả `DB_PASSWORD=` rỗng. Khi chạy từ root chỉ root `.env` được đọc trong bố cục hiện tại. Production nên dùng environment của nền tảng, không phụ thuộc working directory.

`MQTT_BROKER_URL` ưu tiên hơn `MQTT_URI` để tương thích cấu hình cũ. URL backend phải là `tcp://host:port` khi TLS tắt hoặc `ssl://host:port` khi TLS bật (Paho không dùng scheme ESP-IDF `mqtts://`). Sai scheme/TLS, credential nhúng trong URL, password không có username hoặc CA với TLS tắt sẽ bị từ chối ngay lúc khởi tạo. Java source không có broker local hard-code.

TLS dùng trust manager chuẩn JSSE và bật Paho HTTPS endpoint identification để xác minh hostname; không có trust-all hay verifier bỏ qua lỗi. `MQTT_CA_PATH` rỗng dùng JVM trust store; nếu chỉ định thì nạp CA X.509 PEM/DER vào trust store riêng, **thay thế** bộ trust root mặc định cho kết nối MQTT. File CA phải tồn tại, có CA certificate còn hạn; lỗi sẽ làm startup fail, không fallback về TCP. Nên mount CA tại đường dẫn tuyệt đối ngoài source; `classpath:` dành cho CA công khai đã được kiểm duyệt. Không hỗ trợ client certificate/mTLS trong bước này. Xem [quy trình cloud](../docs/production-deployment.md).

`spring.jpa.hibernate.ddl-auto=validate`: chỉ kiểm tra mapping, không tạo/sửa/drop bảng. Không dùng H2 hay database thay thế. PostgreSQL phải đang chạy trước khi khởi động backend.

Backend dùng UTC cho JVM, JDBC, `createdAt`, `lastSeen`, `executedAt`, `observedAt`. Schema hiện tại dùng `TIMESTAMP` không kèm timezone; chuỗi JSON các field này được hiểu là UTC. Múi giờ máy và schema giữ nguyên. Cách này cũng tránh lỗi pgjdbc gửi timezone alias `Asia/Saigon` mà PostgreSQL trong container không chấp nhận.

## Cấu trúc

```text
backend-springboot/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/
├── src/main/resources/application.yml
├── src/main/java/com/smartroom/iot/
│   ├── SmartRoomApplication.java
│   ├── config/        # CORS, UTC Clock
│   ├── controller/    # REST API
│   ├── dto/           # Payload, command request, state response
│   ├── entity/        # Device, Telemetry, Command, Alert
│   ├── exception/     # JSON exception handler
│   ├── mqtt/          # Client lifecycle, subscribe, validation, publish
│   ├── repository/    # PostgreSQL JPA repositories
│   └── service/       # Ingestion, state, commands, presence, alert rule
└── src/test/java/com/smartroom/iot/
    ├── controller/DeviceControllerTest.java
    ├── mqtt/MqttIngressTest.java
    └── service/       # Command, ingestion, state, rule tests
```

## REST API

Base URL: `http://localhost:8080`.

| Method | Endpoint | Kết quả |
|---|---|---|
| GET | `/api/devices` | Danh sách devices |
| GET | `/api/devices/{deviceId}` | Chi tiết device |
| GET | `/api/devices/{deviceId}/telemetry/latest` | Telemetry mới nhất |
| GET | `/api/devices/{deviceId}/telemetry?limit=100` | Lịch sử telemetry, mới nhất trước |
| GET | `/api/devices/{deviceId}/commands?limit=100` | Lịch sử commands |
| POST | `/api/devices/{deviceId}/commands` | Gửi lệnh và trả command record |
| GET | `/api/devices/{deviceId}/state` | Fan/light hiện tại |
| GET | `/api/devices/{deviceId}/alerts?limit=100` | Lịch sử alerts |

History mặc định `limit=100`, cho phép `1..1000`. Record cùng timestamp được sắp thêm theo `id DESC`.

```powershell
Invoke-RestMethod http://localhost:8080/api/devices/smartroom-01/telemetry/latest
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/devices/smartroom-01/commands `
  -ContentType application/json `
  -Body '{"device":"fan","action":"ON"}'
```

Command nhận `device=fan|light`, `action=ON|OFF`, phân biệt chữ hoa/thường. Thành công trả HTTP `201` với `status=SENT`; publish thất bại trả `503` với command record `status=FAILED`. Validation `400`, không tìm thấy device/telemetry `404`, database lỗi `503` có JSON lỗi. Lệnh không hợp lệ không được publish hay insert.

## MQTT và dữ liệu

Subscribe QoS 1:

```text
iot/smartroom/+/telemetry
iot/smartroom/+/state
iot/smartroom/+/status
```

Một scheduled task thử kết nối lại mỗi 5 giây khi mất kết nối hoặc lần đầu không thành công. Kết nối có timeout 5 giây. Mỗi lần kết nối thành công đều subscribe lại và kiểm tra SUBACK; subscribe lỗi cũng được thử lại. Broker local hiện không cần username/password; cloud đọc credential từ môi trường.

Payload phải là JSON, `deviceId` phải trùng device trên topic; enum và khoảng đo DHT22 được validate. Payload sai bị log/reject và không làm ngắt MQTT. Telemetry hợp lệ cập nhật `ONLINE`, `lastSeen`, tạo device nếu chưa có và insert sample trong cùng transaction. JSON `fan` map sang `fan_state`, `light` map sang `light_output_state`.

State cập nhật cache thread-safe sau khi transaction đã commit. `/state` lấy state mới nhất trong cache, dự phòng bằng telemetry gần nhất trong PostgreSQL. Nếu chưa có dữ liệu, trả `fan=null`, `light=null`, `source=UNKNOWN`. Cache state không lưu bền qua restart; backend nhận retained state lại khi subscribe nếu firmware có publish retained. Không cần sửa bảng `devices`.

Publish tới `iot/smartroom/{deviceId}/command` với đúng payload `{"device":"fan","action":"ON"}`, QoS 1 và **không retained**. Command `PENDING` được commit trước khi publish; nhận PUBACK thì lưu `SENT`, lỗi/timeout thì lưu `FAILED`.

`SENT` xác nhận broker nhận lệnh. `executedAt` giữ NULL vì firmware hiện tại không gửi command ID/ack tương quan; xem `/state` hoặc telemetry để xác nhận ESP32 phản hồi. Publish timeout có thể xảy ra dù broker đã nhận lệnh, vì vậy không tự gửi lại command. Backend dừng bất ngờ giữa publish và cập nhật DB có thể để lại record `PENDING`; bước core này chưa có cơ chế khôi phục delivery xuyên restart.

## Alert và presence

- `temperature >= 35.0`: tạo `HIGH_TEMPERATURE`, message `High temperature detected`, `WARNING`, unresolved.
- Giữ khóa dòng device trong transaction để các bản tin đồng thời không tạo nhiều alert unresolved.
- `33.0 <= temperature < 35.0`: giữ trạng thái alert hiện tại.
- `temperature < 33.0`: resolve các alert `HIGH_TEMPERATURE` unresolved của device.
- Rule không tự bật quạt.
- Mỗi 15 giây, đánh dấu `OFFLINE` nếu `lastSeen` cũ hơn 30 giây. Do chu kỳ quét, thời điểm đổi OFFLINE có thể trễ thêm tối đa khoảng 15 giây.
- Status JSON `OFFLINE` từ MQTT cũng được ghi nhận ngay.

## Kiểm thử

`mvnw.cmd verify` chạy unit/MVC tests bằng repository và MQTT mocks: ngưỡng nhiệt độ/hysteresis/dedup, publish thành công/thất bại, presence cutoff, state fallback, payload không hợp lệ, HTTP errors và CORS. Không insert telemetry giả vào PostgreSQL và không dùng database giả. Kết nối JPA/PostgreSQL và luồng phần cứng được kiểm tra riêng bằng backend đang chạy, MQTT observation và REST.

Tests bổ sung kiểm tra local anonymous, chuyển credential nguyên vẹn, fail-fast cấu hình TLS và reconnect/re-subscribe. TLS handshake chạy thật qua Paho trên loopback với identity tự sinh trong temp directory: CA đúng/hostname đúng được chấp nhận, CA không tin cậy hoặc hostname sai bị từ chối. Không cần cloud broker; không kết nối PostgreSQL/EMQX trong tests này. Cần JDK 17 đầy đủ có `keytool`; private key test không được lưu vào source.

Log build: `target/build-verification.log`. Log phiên chạy kiểm thử: `target/backend.log`, `target/backend-error.log`.

Dependencies chính: Spring Web (bao gồm Jackson), Spring Data JPA, PostgreSQL JDBC, Validation, Eclipse Paho MQTT 1.2.5; JUnit 5/Mockito/MockMvc qua Spring Boot Starter Test. Frontend nằm tại `../frontend-react`. MQTT credential không bổ sung authentication cho REST API.
