# Chuẩn bị triển khai SmartRoom IoT lên cloud

Source đã hỗ trợ cấu hình MQTT Cloud; runtime local chưa được chuyển. Chưa có hostname, tài khoản hay CA cloud thật. Không tạo broker, deploy, đổi database hoặc flash firmware trong bước chuẩn bị này.

## Kiến trúc

```text
ESP32-C3 ⇄ MQTT Cloud (TLS) ⇄ Spring Boot ⇄ PostgreSQL Cloud
                                  ⇅ HTTPS REST
                             React / Vercel
```

Telemetry/state/status đi từ ESP32 tới backend; command đi theo chiều ngược lại. React chỉ gọi REST API, không giữ MQTT credential. Firmware ESP32 hiện **chưa có trong repository**: phần dưới là hướng dẫn để áp dụng khi có source được chủ project cung cấp, chưa phải firmware đã build/test.

## Environment variables

| Biến | Local / yêu cầu cloud |
|---|---|
| `DB_URL` | Local `jdbc:postgresql://localhost:5433/smartroom_iot`; cloud dùng JDBC URL nhà cung cấp, TLS `sslmode=verify-full` và trust CA phù hợp |
| `DB_USERNAME` | Local `smartroom`; cloud lấy tài khoản riêng từ PostgreSQL provider |
| `DB_PASSWORD` | Mật khẩu database hiện có, không có mặc định trong source |
| `SERVER_PORT` | Mặc định `8080`, đặt theo nền tảng backend |
| `MQTT_BROKER_URL` | Local `tcp://192.168.130.174:1883`; cloud `ssl://<MQTT_HOST>:8883` với hostname/port thật do provider cấp |
| `MQTT_CLIENT_ID` | Local `smartroom-backend`; mỗi backend/ESP32/MQTTX có ID khác nhau để tránh đá kết nối |
| `MQTT_USERNAME` | Local để rỗng; cloud điền username thật bằng secret environment |
| `MQTT_PASSWORD` | Local để rỗng; cloud điền password thật bằng secret environment |
| `MQTT_TLS_ENABLED` | Local `false`; cloud `true` |
| `MQTT_CA_PATH` | Rỗng dùng JVM trust store; CA riêng dùng đường dẫn tuyệt đối hoặc `file:` URI; có hỗ trợ `classpath:` cho public CA đã kiểm duyệt |
| `CORS_ORIGIN` | Local `http://localhost:5173`; production dùng origin HTTPS frontend thực tế |
| `VITE_API_BASE_URL` | Local `http://localhost:8080`; production dùng URL HTTPS backend, không phải địa chỉ MQTT |

`MQTT_URI` là alias tương thích cũ, chỉ dùng khi `MQTT_BROKER_URL` chưa được đặt. Không đặt đồng thời nếu không cần. Paho dùng `tcp://` / `ssl://`; ESP-IDF dùng `mqtt://` / `mqtts://`, cùng hostname/port nhưng khác scheme.

Không copy đè `.env` đang dùng. Root `.env` hiện chứa mật khẩu DB local và được ignore. Backend đọc root `.env` khi chạy từ root hoặc thư mục backend; backend `.env` nếu có sẽ ghi đè root khi chạy trong backend. File example có `DB_PASSWORD=` rỗng nên phải điền mật khẩu hiện có nếu copy. Các file `.env` được Spring đọc theo định dạng Java properties, không phải shell script: không dùng `export`, không bọc giá trị trong dấu nháy; escape dấu `\` nếu cần. Production ưu tiên environment/secret manager của nền tảng để bảo toàn password có ký tự đặc biệt, không ghi vào command line/log.

Ví dụ chuyển MQTT bằng environment sau khi đã có thông tin thật; mọi giá trị trong `<...>` dưới đây chỉ là placeholder, chưa dùng để kết nối:

```ini
MQTT_BROKER_URL=ssl://<MQTT_HOST>:8883
MQTT_CLIENT_ID=smartroom-backend
MQTT_USERNAME=<MQTT_USERNAME_FROM_PROVIDER>
MQTT_PASSWORD=<MQTT_PASSWORD_FROM_PROVIDER>
MQTT_TLS_ENABLED=true
MQTT_CA_PATH=
```

## TLS backend

- JSSE xác minh certificate chain và Paho bật `setHttpsHostnameVerificationEnabled(true)` để so hostname. Dùng DNS hostname trong certificate, không thay bằng IP nếu certificate không có IP SAN.
- Khi CA path rỗng, dùng trust store JVM. Khi có CA, chỉ các CA trong file này được tin cậy cho MQTT; không thay trust store toàn JVM. Hỗ trợ CA X.509 PEM/DER và nhiều CA trong cùng file.
- Mount CA từ secret/config volume tại đường dẫn tuyệt đối; với Windows có thể dùng `D:/secure/mqtt-ca.crt`, Linux `/run/secrets/mqtt-ca.crt`. Không tải certificate từ endpoint chưa xác thực rồi tự coi là trusted; lấy CA từ provider qua kênh tin cậy.
- Cấu hình TLS sai scheme, CA thiếu/hỏng/hết hạn hoặc CA path khi TLS tắt làm startup fail. Certificate chain/hostname sai làm handshake fail và reconnect theo lịch; không hạ xuống TCP.
- Không có trust-all, không bỏ hostname verification. Bước này hỗ trợ username/password và server TLS; chưa hỗ trợ client certificate/mTLS.
- Root `.gitignore` và backend ignore `.env`, private key, certificate `.pem/.crt/.cer`, `private/`, `secrets/`. Public CA không phải secret nhưng mặc định cũng không commit để tránh lẫn private cert/key. `.env.example` được phép commit.

## Migration firmware khi có source

Giữ profile LOCAL `mqtt://192.168.130.174:1883` không credential và profile CLOUD `mqtts://<MQTT_HOST>:8883` có username/password/TLS. Profile phải chọn rõ ràng; lỗi cloud không được tự fallback về local hoặc bỏ TLS. Không thay pin, DHT22, light module, fan L298N, LED hoặc logic actuator.

Với ESP-IDF 5.5.x, dùng cấu trúc `esp_mqtt_client_config_t`: `broker.address.uri`, `credentials.username`, `credentials.authentication.password`, `credentials.client_id`. Ưu tiên certificate bundle chuẩn: bật `CONFIG_MBEDTLS_CERTIFICATE_BUNDLE`, đặt `broker.verification.crt_bundle_attach = esp_crt_bundle_attach` và include `esp_crt_bundle.h`. Nếu provider dùng CA riêng chưa có trong bundle, nạp public CA vào `broker.verification.certificate` hoặc custom bundle; không nhúng private key/credential vào tracked source. Không bật `skip_cert_common_name_check` hay chế độ bỏ xác thực server. Đồng bộ thời gian trước TLS để kiểm tra hạn certificate.

Credential firmware lấy từ provisioning/NVS hoặc file local được ignore khi build; ESP32 không tự đọc environment của Spring Boot. Khi có source mới chọn cách provisioning phù hợp, không tự viết lại firmware đang hoạt động.

Giữ reconnect của ESP-MQTT; mỗi `MQTT_EVENT_CONNECTED` phải subscribe lại topic command và kiểm tra `MQTT_EVENT_SUBSCRIBED`. Giữ LWT OFFLINE/ONLINE và payload hiện có, không đổi QoS/retain nếu chưa kiểm tra firmware. Command không retained; không tự replay command sau reconnect.

| Topic giữ nguyên | Hướng |
|---|---|
| `iot/smartroom/smartroom-01/telemetry` | ESP32 publish, backend subscribe |
| `iot/smartroom/smartroom-01/state` | ESP32 publish, backend subscribe |
| `iot/smartroom/smartroom-01/status` | ESP32 publish/LWT, backend subscribe |
| `iot/smartroom/smartroom-01/command` | Backend publish, ESP32 subscribe |

Backend hiện subscribe `iot/smartroom/+/telemetry`, `+/state`, `+/status`. ACL phải cho phép các subscription filter này hoặc cần điều chỉnh phạm vi trong một thay đổi riêng. Đề xuất tài khoản riêng cho backend và mỗi ESP32: backend chỉ publish command, ESP32 chỉ subscribe command của chính nó và publish ba topic còn lại. Không cấp quyền quản trị broker cho client.

## Quy trình chuyển đổi sau này

1. Tạo MQTT Cloud deployment qua nhà cung cấp bạn chọn; giữ local nguyên vẹn trong giai đoạn chuẩn bị.
2. Lấy DNS hostname, port TLS và thông tin MQTT protocol/giới hạn kết nối. Không dùng host ví dụ trong tài liệu.
3. Tạo username/password riêng và ACL cho backend, ESP32, MQTTX; mỗi client có client ID duy nhất.
4. Lấy CA certificate nếu provider yêu cầu, xác minh nguồn và hạn; quyết định JVM trust store hoặc CA file, certificate bundle hoặc CA riêng trên ESP32.
5. Điền environment backend bằng secret manager; giữ `DB_*` local nếu chỉ chuyển MQTT. Chuyển PostgreSQL Cloud là công việc riêng có backup/migration, không chạy init script lên DB hiện có. Cấu hình REST HTTPS, authentication/authorization trước khi public API điều khiển ra Internet; CORS không thay thế authentication.
6. Khi có source firmware, thêm hai profile và cách provisioning ở trên; backup cấu hình hiện tại, build với ESP-IDF 5.5.x, chỉ flash khi bạn chủ động chuyển đổi.
7. Test MQTTX tới broker TLS bằng credential riêng; bật CA/hostname verification, xác nhận connect và ACL. Chỉ dùng topic chẩn đoán được cấp phép; không phát telemetry giả vào topic lưu DB hay gửi command actuator ngoài buổi test được cho phép.
8. Test ESP32 trên cloud: TLS, publish telemetry thật, state/status, LWT, reconnect và re-subscribe command. Kiểm tra cảm biến/actuator vật lý giữ hành vi cũ.
9. Test backend: kết nối TLS, subscription SUBACK, telemetry thật vào API, lastSeen/ONLINE, lỗi TLS bị từ chối. Kiểm tra POST command trong buổi test được phép; `SENT` chỉ xác nhận broker, `executedAt` vẫn NULL khi firmware chưa có correlated ACK.
10. Test dashboard với `VITE_API_BASE_URL` production, CORS origin đúng, HTTPS, telemetry/state/alerts và command; trên Vercel chọn root `frontend-react`, build `npm run build`, output `dist`, giữ SPA rewrite. Browser không được chứa MQTT/DB secret.

Rollback MQTT: khôi phục environment đã backup (`MQTT_BROKER_URL=tcp://192.168.130.174:1883`, `MQTT_TLS_ENABLED=false`, username/password/CA rỗng), chọn firmware LOCAL khi có source và thực hiện restart/flash có chủ đích trong buổi migration. Không xóa database/volume hoặc EMQX để rollback.

## Kiểm chứng source

```powershell
# backend-springboot/
.\mvnw.cmd verify
# frontend-react/
npm run typecheck
npm run build
```

TLS tests tạo identity tạm, handshake Paho thật trên loopback, kiểm tra đúng CA/hostname và từ chối CA/hostname sai. Không cần credential cloud thật; đây chưa phải xác nhận cloud deployment hoạt động. Local regression chỉ quan sát telemetry thật và GET API, không publish command hoặc ghi DB trực tiếp.

## Tài liệu chính thức

- [Paho 1.2.5 SSLNetworkModule: hostname verification](https://github.com/eclipse-paho/paho.mqtt.java/blob/v1.2.5/org.eclipse.paho.client.mqttv3/src/main/java/org/eclipse/paho/client/mqttv3/internal/SSLNetworkModule.java)
- [ESP-IDF 5.5.1 ESP-MQTT: URI, verification, credentials và events](https://docs.espressif.com/projects/esp-idf/en/v5.5.1/esp32c3/api-reference/protocols/mqtt.html)
- [ESP-IDF 5.5.1 certificate bundle](https://docs.espressif.com/projects/esp-idf/en/v5.5.1/esp32c3/api-reference/protocols/esp_crt_bundle.html)
