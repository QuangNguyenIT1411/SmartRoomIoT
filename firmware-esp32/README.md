# SmartRoom IoT — ESP32-C3 cloud firmware

Source được copy có chọn lọc từ `D:\iot-projects\Lab203`: hai CMakeLists và `main/hello_world_main.c`. Không copy build, sdkconfig sinh tự động, IDE metadata hoặc credential Wi-Fi gốc. Project Lab203 không bị sửa. Bản này dùng ESP-IDF **v5.5.5**, target **esp32c3**.

## Phần cứng và logic được giữ nguyên

| Kết nối | GPIO |
|---|---|
| DHT22 DATA | 3 |
| Light relay NO (COM nối GND), input pull-up | 1 |
| L298N IN1 / IN2 / ENA | 4 / 5 / 6 |
| LED | 7 |

Giữ nguyên hàm đọc DHT22, timing, checksum, `dht_fix_shift`, ưu tiên frame đã sửa lệch 1 bit, kiểm tra khoảng đo và fallback về sample hợp lệ trước đó. Giữ nguyên GPIO initialization, fan/LED functions, command processing và telemetry task khoảng 5 giây. Không viết lại DHT22. Fan và LED khởi động OFF.

## MQTT Cloud và TLS

- URI: `mqtts://kca70f7a.ala.asia-southeast1.emqxsl.com:8883`.
- Client ID: `smartroom-01`; backend dùng `smartroom-backend`, MQTTX phải dùng ID khác.
- Username: macro `MQTT_USERNAME` trong local `secrets.h`, giá trị được cung cấp là `smartroom-device`.
- Password: macro `MQTT_PASSWORD`, bạn tự điền; không có password thật trong source được commit.
- TLS dùng `esp_crt_bundle_attach` với full certificate bundle chuẩn của ESP-IDF 5.5.5. `skip_cert_common_name_check=false`, không trust-all, không insecure TLS hoặc bỏ hostname verification.
- Đồng bộ thời gian SNTP qua `pool.ntp.org` trước MQTT để kiểm tra hạn certificate. Nếu SNTP chưa xong, chờ và báo log mỗi 30 giây; cần mạng cho DNS/UDP 123. Không hạ bảo mật để vượt lỗi TLS.
- Keepalive 60 giây; MQTT auto-reconnect 5 giây; Wi-Fi tự gọi connect lại khi mất kết nối. Mỗi MQTT connected đều gửi subscribe command lại; log SUBACK giúp kiểm tra broker đã trả lời.
- Last Will: status OFFLINE, QoS 1, retained. Mỗi connect/reconnect publish ONLINE retained và state retained. Broker phát LWT khi mất kết nối bất thường; có thể cần chờ keepalive timeout để nhận OFFLINE.

| Topic | Nội dung |
|---|---|
| `iot/smartroom/smartroom-01/telemetry` | Publish QoS 1, không retained, khoảng 5 giây khi có DHT data |
| `iot/smartroom/smartroom-01/command` | Subscribe QoS 1 sau mỗi connect/reconnect |
| `iot/smartroom/smartroom-01/state` | Publish QoS 1, retained, sau connect và command |
| `iot/smartroom/smartroom-01/status` | ONLINE / LWT OFFLINE, QoS 1, retained |

Payload giữ nguyên:

```json
{"deviceId":"smartroom-01","temperature":30.1,"humidity":81.3,"lightState":"ACTIVE","fan":"OFF","light":"OFF"}
```

```json
{"device":"fan","action":"ON"}
```

Command hỗ trợ `fan`/`light`, `ON`/`OFF`; sau xử lý publish state ngay. Ví dụ state: `{"deviceId":"smartroom-01","fan":"ON","light":"OFF"}`. Status: `{"deviceId":"smartroom-01","status":"ONLINE"}` hoặc `OFFLINE`. Command thiếu/oversize/fragmented bị bỏ qua để tránh xử lý JSON bị cắt. Không publish retained command từ MQTTX.

## Điền secrets local

File bạn cần sửa:

`D:\iot-projects\SmartRoomIoT\firmware-esp32\main\secrets.h`

File đã được tạo từ `main/secrets.example.h` nếu chưa có. Bạn tự điền:

- `WIFI_SSID`: tên mạng Wi-Fi 2.4 GHz.
- `WIFI_PASSWORD`: mật khẩu Wi-Fi.
- `MQTT_PASSWORD`: password của account cloud `smartroom-device`.
- `MQTT_USERNAME` hiện là `smartroom-device`; chỉ đổi nếu nhà cung cấp cấp account khác.

Giữ giá trị trong C string; escape `"` và `\` khi chúng thuộc password. Không commit hoặc gửi file secrets, build binary, ELF hay build directory: credential được nhúng vào firmware lúc compile. Root `.gitignore` loại `main/secrets.h`, `build/`, `sdkconfig`, `sdkconfig.old`; vẫn commit `secrets.example.h` và `sdkconfig.defaults`.

Build với placeholder được phép để kiểm tra compiler/linker. Runtime có guard: nếu vẫn còn placeholder hoặc credential rỗng thì giữ outputs OFF và dừng trước Wi-Fi/MQTT, chỉ báo tên file cần điền, không log password.

## Build trên máy hiện tại

Trong PowerShell, kích hoạt môi trường đã cài:

```powershell
. 'C:\Espressif\tools\Microsoft.v5.5.5.PowerShell_profile.ps1'
Set-Location D:\iot-projects\SmartRoomIoT\firmware-esp32
idf.py --version
idf.py set-target esp32c3
idf.py fullclean
idf.py build
```

`--version` phải là v5.5.5. Trên máy khác, kích hoạt ESP-IDF 5.5.5 bằng script của máy đó. `sdkconfig.defaults` giữ cấu hình flash 2 MB, partition single-app và FreeRTOS 100 Hz từ Lab203; bật MQTT TLS, certificate bundle, certificate date validation. Không chép sdkconfig/build của Lab203 sang.

## Flash và monitor sau khi điền secrets

Chưa flash tự động. Sau khi bạn tự điền secrets, dùng PowerShell đã kích hoạt ESP-IDF:

```powershell
Set-Location D:\iot-projects\SmartRoomIoT\firmware-esp32
idf.py build
if ($LASTEXITCODE -eq 0) {
    $devicePort = Read-Host 'Nhap cong COM cua ESP32 (vi du COM5)'
    idf.py -p $devicePort flash monitor
}
```

Nếu chỉ mở monitor: `idf.py -p COM5 monitor` (thay COM5 bằng cổng thật). Thoát monitor bằng Ctrl+]. Không cần erase-flash.

Sau flash, kiểm tra lần lượt Wi-Fi → time synchronized → MQTT connected → SUBACK → ONLINE/state → telemetry thật. Test fan/light ON/OFF từ MQTTX bằng account có quyền publish command, theo dõi state và thiết bị vật lý. Ngắt/khôi phục Wi-Fi để kiểm tra reconnect/re-subscribe/LWT. Nếu lỗi TLS, kiểm tra thời gian, hostname, certificate và mạng; nếu lỗi auth/subscribe, kiểm tra password/ACL từ provider.

Backend hiện vẫn dùng MQTT local. Dashboard local sẽ không thấy telemetry cloud từ thiết bị sau khi chuyển firmware cho tới khi bạn chủ động chuyển backend sang cùng cloud broker; bước này không sửa backend/frontend/database.

## Phạm vi kiểm chứng

Build kiểm tra source với secrets placeholder; chưa xác nhận Wi-Fi/authentication/actuator/reconnect trên phần cứng cho bản cloud vì chưa điền password hoặc flash. Có thể kiểm tra TLS riêng từ máy tính với CA bundle của ESP-IDF mà không gửi MQTT credential; kết quả đó không thay thế kiểm tra trên ESP32.

Build v5.5.5 hiện PASS: binary `0xfeac0` bytes, partition app `0x100000` bytes, còn `0x1540` bytes (khoảng 1%). Giữ partition hiện có của Lab203; cần theo dõi dung lượng khi thêm tính năng. Sau khi sửa secrets phải build lại và chỉ flash nếu build PASS.
