#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <strings.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/event_groups.h"

#include "driver/gpio.h"

#include "esp_log.h"
#include "esp_timer.h"
#include "esp_rom_sys.h"

#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_netif_sntp.h"
#include "esp_crt_bundle.h"

#include "nvs_flash.h"

#include "mqtt_client.h"
#include "cJSON.h"


// ============================================================
// DEVICE
// ============================================================

#define DEVICE_ID "smartroom-01"


// ============================================================
// WIFI
// ============================================================

#include "secrets.h"


// ============================================================
// MQTT
// ============================================================

// MQTT Cloud TLS; credentials are only in ignored secrets.h.
#define MQTT_BROKER_URI \
    "mqtts://kca70f7a.ala.asia-southeast1.emqxsl.com:8883"


#define MQTT_TOPIC_TELEMETRY \
    "iot/smartroom/smartroom-01/telemetry"

#define MQTT_TOPIC_COMMAND \
    "iot/smartroom/smartroom-01/command"

#define MQTT_TOPIC_STATE \
    "iot/smartroom/smartroom-01/state"

#define MQTT_TOPIC_STATUS \
    "iot/smartroom/smartroom-01/status"


// ============================================================
// GPIO
// ============================================================

// DHT22
#define DHT_PIN \
    GPIO_NUM_3

// Relay cảm biến ánh sáng:
// NO  -> GPIO1
// COM -> GND
#define LIGHT_SENSOR_PIN \
    GPIO_NUM_1

// L298N
#define FAN_IN1 \
    GPIO_NUM_4

#define FAN_IN2 \
    GPIO_NUM_5

#define FAN_ENA \
    GPIO_NUM_6

// LED
#define LED_PIN \
    GPIO_NUM_7


// ============================================================
// GLOBAL
// ============================================================

static const char *TAG = "SMART_ROOM";

static EventGroupHandle_t wifi_event_group;

#define WIFI_CONNECTED_BIT BIT0


static esp_mqtt_client_handle_t mqtt_client =
    NULL;


static volatile bool mqtt_connected =
    false;


static bool fan_is_on =
    false;

static bool led_is_on =
    false;


static float last_temperature =
    0.0f;

static float last_humidity =
    0.0f;

static bool have_dht_data =
    false;


// ============================================================
// GPIO WAIT
// ============================================================

static bool wait_for_level(
    gpio_num_t pin,
    int level,
    uint32_t timeout_us
)
{
    int64_t start =
        esp_timer_get_time();

    while (
        gpio_get_level(pin) != level
    )
    {
        if (
            esp_timer_get_time() - start
            >= timeout_us
        )
        {
            return false;
        }
    }

    return true;
}


// ============================================================
// GPIO MEASURE
// ============================================================

static int measure_level_us(
    gpio_num_t pin,
    int level,
    uint32_t timeout_us
)
{
    int64_t start =
        esp_timer_get_time();

    while (
        gpio_get_level(pin) == level
    )
    {
        if (
            esp_timer_get_time() - start
            >= timeout_us
        )
        {
            return -1;
        }
    }

    return (int)(
        esp_timer_get_time() - start
    );
}


// ============================================================
// DHT CHECKSUM
// ============================================================

static bool dht_checksum_ok(
    const uint8_t data[5]
)
{
    uint8_t checksum =
        (
            data[0]
            + data[1]
            + data[2]
            + data[3]
        )
        & 0xFF;

    return checksum == data[4];
}


// ============================================================
// DHT FIX 1 BIT
// ============================================================

static void dht_fix_shift(
    const uint8_t input[5],
    uint8_t output[5]
)
{
    output[0] =
        input[0] >> 1;

    for (
        int i = 1;
        i < 5;
        i++
    )
    {
        output[i] =
            (input[i] >> 1)
            |
            (
                (input[i - 1] & 0x01)
                << 7
            );
    }
}


// ============================================================
// DHT DECODE
// ============================================================

static void dht_decode(
    const uint8_t data[5],
    float *temperature,
    float *humidity
)
{
    uint16_t raw_humidity =
        (
            ((uint16_t)data[0]) << 8
        )
        |
        data[1];


    *humidity =
        raw_humidity / 10.0f;


    uint16_t raw_temperature =
        (
            ((uint16_t)(data[2] & 0x7F))
            << 8
        )
        |
        data[3];


    *temperature =
        raw_temperature / 10.0f;


    if (
        data[2] & 0x80
    )
    {
        *temperature =
            -*temperature;
    }
}


// ============================================================
// DHT VALUE VALIDATION
// ============================================================

static bool dht_values_valid(
    float temperature,
    float humidity
)
{
    if (
        humidity < 0.0f
        ||
        humidity > 100.0f
    )
    {
        return false;
    }


    if (
        temperature < -40.0f
        ||
        temperature > 80.0f
    )
    {
        return false;
    }


    return true;
}


// ============================================================
// SELECT DHT FRAME
// ============================================================

static bool dht_select_frame(
    const uint8_t raw[5],
    float *temperature,
    float *humidity
)
{
    uint8_t fixed[5] = {0};


    dht_fix_shift(
        raw,
        fixed
    );


    float raw_t = 0.0f;
    float raw_h = 0.0f;

    float fix_t = 0.0f;
    float fix_h = 0.0f;


    bool raw_valid = false;
    bool fix_valid = false;


    // RAW
    if (
        dht_checksum_ok(raw)
    )
    {
        dht_decode(
            raw,
            &raw_t,
            &raw_h
        );


        raw_valid =
            dht_values_valid(
                raw_t,
                raw_h
            );
    }


    // FIX
    if (
        dht_checksum_ok(fixed)
    )
    {
        dht_decode(
            fixed,
            &fix_t,
            &fix_h
        );


        fix_valid =
            dht_values_valid(
                fix_t,
                fix_h
            );
    }


    // Board hiện tại đang thường lệch 1 bit
    if (
        fix_valid
    )
    {
        *temperature =
            fix_t;

        *humidity =
            fix_h;

        return true;
    }


    if (
        raw_valid
    )
    {
        *temperature =
            raw_t;

        *humidity =
            raw_h;

        return true;
    }


    return false;
}


// ============================================================
// READ DHT22
// ============================================================

static bool dht22_read(
    float *temperature,
    float *humidity
)
{
    uint8_t raw[5] = {0};


    // Host start
    gpio_set_direction(
        DHT_PIN,
        GPIO_MODE_OUTPUT
    );


    gpio_set_level(
        DHT_PIN,
        1
    );

    esp_rom_delay_us(
        1000
    );


    gpio_set_level(
        DHT_PIN,
        0
    );


    vTaskDelay(
        pdMS_TO_TICKS(20)
    );


    gpio_set_level(
        DHT_PIN,
        1
    );


    esp_rom_delay_us(
        30
    );


    // Input
    gpio_set_direction(
        DHT_PIN,
        GPIO_MODE_INPUT
    );


    gpio_set_pull_mode(
        DHT_PIN,
        GPIO_PULLUP_ONLY
    );


    // Response LOW
    if (
        !wait_for_level(
            DHT_PIN,
            0,
            200
        )
    )
    {
        return false;
    }


    if (
        measure_level_us(
            DHT_PIN,
            0,
            200
        )
        < 0
    )
    {
        return false;
    }


    // Response HIGH
    if (
        !wait_for_level(
            DHT_PIN,
            1,
            200
        )
    )
    {
        return false;
    }


    if (
        measure_level_us(
            DHT_PIN,
            1,
            200
        )
        < 0
    )
    {
        return false;
    }


    // 40 bits
    for (
        int bit = 0;
        bit < 40;
        bit++
    )
    {
        if (
            !wait_for_level(
                DHT_PIN,
                1,
                120
            )
        )
        {
            return false;
        }


        esp_rom_delay_us(
            40
        );


        int value =
            gpio_get_level(
                DHT_PIN
            );


        raw[
            bit / 8
        ] <<= 1;


        if (
            value
        )
        {
            raw[
                bit / 8
            ] |= 1;
        }


        if (
            bit < 39
        )
        {
            if (
                !wait_for_level(
                    DHT_PIN,
                    0,
                    120
                )
            )
            {
                return false;
            }
        }
    }


    return dht_select_frame(
        raw,
        temperature,
        humidity
    );
}


// ============================================================
// FAN
// ============================================================

static void fan_on(void)
{
    gpio_set_level(
        FAN_IN1,
        1
    );

    gpio_set_level(
        FAN_IN2,
        0
    );

    gpio_set_level(
        FAN_ENA,
        1
    );

    fan_is_on =
        true;


    ESP_LOGI(
        TAG,
        "FAN -> ON"
    );
}


static void fan_off(void)
{
    gpio_set_level(
        FAN_ENA,
        0
    );

    gpio_set_level(
        FAN_IN1,
        0
    );

    gpio_set_level(
        FAN_IN2,
        0
    );

    fan_is_on =
        false;


    ESP_LOGI(
        TAG,
        "FAN -> OFF"
    );
}


// ============================================================
// LED
// ============================================================

static void led_on(void)
{
    gpio_set_level(
        LED_PIN,
        1
    );

    led_is_on =
        true;


    ESP_LOGI(
        TAG,
        "LIGHT -> ON"
    );
}


static void led_off(void)
{
    gpio_set_level(
        LED_PIN,
        0
    );

    led_is_on =
        false;


    ESP_LOGI(
        TAG,
        "LIGHT -> OFF"
    );
}


// ============================================================
// LIGHT SENSOR
// ============================================================

static bool light_sensor_active(void)
{
    return (
        gpio_get_level(
            LIGHT_SENSOR_PIN
        )
        == 0
    );
}


// ============================================================
// MQTT PUBLISH STATUS
// ============================================================

static void mqtt_publish_status(void)
{
    if (
        !mqtt_connected
        ||
        mqtt_client == NULL
    )
    {
        return;
    }


    char payload[128];


    snprintf(
        payload,
        sizeof(payload),
        "{"
        "\"deviceId\":\"%s\","
        "\"status\":\"ONLINE\""
        "}",
        DEVICE_ID
    );


    esp_mqtt_client_publish(
        mqtt_client,
        MQTT_TOPIC_STATUS,
        payload,
        0,
        1,
        1
    );


    ESP_LOGI(
        TAG,
        "STATUS -> %s",
        payload
    );
}


// ============================================================
// MQTT PUBLISH STATE
// ============================================================

static void mqtt_publish_state(void)
{
    if (
        !mqtt_connected
        ||
        mqtt_client == NULL
    )
    {
        return;
    }


    char payload[160];


    snprintf(
        payload,
        sizeof(payload),

        "{"
        "\"deviceId\":\"%s\","
        "\"fan\":\"%s\","
        "\"light\":\"%s\""
        "}",

        DEVICE_ID,

        fan_is_on
            ? "ON"
            : "OFF",

        led_is_on
            ? "ON"
            : "OFF"
    );


    esp_mqtt_client_publish(
        mqtt_client,
        MQTT_TOPIC_STATE,
        payload,
        0,
        1,
        1
    );


    ESP_LOGI(
        TAG,
        "STATE -> %s",
        payload
    );
}


// ============================================================
// MQTT PUBLISH TELEMETRY
// ============================================================

static void mqtt_publish_telemetry(
    float temperature,
    float humidity
)
{
    if (
        !mqtt_connected
        ||
        mqtt_client == NULL
    )
    {
        return;
    }


    bool light_active =
        light_sensor_active();


    char payload[256];


    snprintf(
        payload,
        sizeof(payload),

        "{"
        "\"deviceId\":\"%s\","
        "\"temperature\":%.1f,"
        "\"humidity\":%.1f,"
        "\"lightState\":\"%s\","
        "\"fan\":\"%s\","
        "\"light\":\"%s\""
        "}",

        DEVICE_ID,

        temperature,

        humidity,

        light_active
            ? "ACTIVE"
            : "INACTIVE",

        fan_is_on
            ? "ON"
            : "OFF",

        led_is_on
            ? "ON"
            : "OFF"
    );


    esp_mqtt_client_publish(
        mqtt_client,
        MQTT_TOPIC_TELEMETRY,
        payload,
        0,
        1,
        0
    );


    ESP_LOGI(
        TAG,
        "TELEMETRY -> %s",
        payload
    );
}


// ============================================================
// PROCESS COMMAND
// ============================================================

static void process_command(
    const char *payload
)
{
    ESP_LOGI(
        TAG,
        "COMMAND <- %s",
        payload
    );


    cJSON *root =
        cJSON_Parse(
            payload
        );


    if (
        root == NULL
    )
    {
        ESP_LOGE(
            TAG,
            "Invalid command JSON"
        );

        return;
    }


    cJSON *device =
        cJSON_GetObjectItemCaseSensitive(
            root,
            "device"
        );


    cJSON *action =
        cJSON_GetObjectItemCaseSensitive(
            root,
            "action"
        );


    if (
        !cJSON_IsString(device)
        ||
        !cJSON_IsString(action)
    )
    {
        ESP_LOGE(
            TAG,
            "Missing device/action"
        );


        cJSON_Delete(
            root
        );

        return;
    }


    // ========================================================
    // FAN
    // ========================================================

    if (
        strcasecmp(
            device->valuestring,
            "fan"
        )
        == 0
    )
    {
        if (
            strcasecmp(
                action->valuestring,
                "ON"
            )
            == 0
        )
        {
            fan_on();
        }

        else if (
            strcasecmp(
                action->valuestring,
                "OFF"
            )
            == 0
        )
        {
            fan_off();
        }
    }


    // ========================================================
    // LIGHT
    // ========================================================

    else if (
        strcasecmp(
            device->valuestring,
            "light"
        )
        == 0
    )
    {
        if (
            strcasecmp(
                action->valuestring,
                "ON"
            )
            == 0
        )
        {
            led_on();
        }

        else if (
            strcasecmp(
                action->valuestring,
                "OFF"
            )
            == 0
        )
        {
            led_off();
        }
    }


    cJSON_Delete(
        root
    );


    // Báo lại trạng thái ngay
    mqtt_publish_state();
}


// ============================================================
// MQTT EVENT
// ============================================================

static void mqtt_event_handler(
    void *handler_args,
    esp_event_base_t base,
    int32_t event_id,
    void *event_data
)
{
    esp_mqtt_event_handle_t event =
        event_data;


    switch (
        (esp_mqtt_event_id_t)event_id
    )
    {
        case MQTT_EVENT_CONNECTED:
        {
            mqtt_connected =
                true;


            ESP_LOGI(
                TAG,
                "================================"
            );

            ESP_LOGI(
                TAG,
                "MQTT CONNECTED"
            );

            ESP_LOGI(
                TAG,
                "================================"
            );


            // Re-subscribe mỗi lần reconnect
            int subscribe_id = esp_mqtt_client_subscribe(
                event->client,
                MQTT_TOPIC_COMMAND,
                1
            );


            ESP_LOGI(
                TAG,
                "Command subscription requested: %s (id=%d)",
                MQTT_TOPIC_COMMAND,
                subscribe_id
            );

            if (subscribe_id < 0) {
                ESP_LOGE(TAG, "Command subscription could not be queued");
            }


            mqtt_publish_status();

            mqtt_publish_state();

            break;
        }


        case MQTT_EVENT_SUBSCRIBED:
        {
            ESP_LOGI(TAG, "Command SUBACK received (id=%d)", event->msg_id);
            break;
        }

        case MQTT_EVENT_DISCONNECTED:
        {
            mqtt_connected =
                false;


            ESP_LOGW(
                TAG,
                "MQTT DISCONNECTED"
            );

            break;
        }


        case MQTT_EVENT_DATA:
        {
            char topic[160];
            char payload[256];

            // Commands are small complete JSON messages; reject truncated/fractured input.
            if (event->current_data_offset != 0
                    || event->data_len != event->total_data_len
                    || event->data_len <= 0 || event->data_len >= sizeof(payload)
                    || event->topic_len <= 0 || event->topic_len >= sizeof(topic)) {
                ESP_LOGW(TAG, "Ignoring incomplete or oversized command");
                break;
            }


            int topic_len =
                event->topic_len;

            int data_len =
                event->data_len;


            if (
                topic_len
                >= sizeof(topic)
            )
            {
                topic_len =
                    sizeof(topic) - 1;
            }


            if (
                data_len
                >= sizeof(payload)
            )
            {
                data_len =
                    sizeof(payload) - 1;
            }


            memcpy(
                topic,
                event->topic,
                topic_len
            );

            topic[topic_len] =
                '\0';


            memcpy(
                payload,
                event->data,
                data_len
            );

            payload[data_len] =
                '\0';


            if (
                strcmp(
                    topic,
                    MQTT_TOPIC_COMMAND
                )
                == 0
            )
            {
                process_command(
                    payload
                );
            }


            break;
        }


        case MQTT_EVENT_ERROR:
        {
            ESP_LOGE(
                TAG,
                "MQTT ERROR"
            );

            if (event->error_handle != NULL) {
                ESP_LOGE(TAG, "MQTT error type=%d, TLS=%d, verify_flags=0x%x, broker_code=%d",
                         event->error_handle->error_type,
                         event->error_handle->esp_tls_last_esp_err,
                         event->error_handle->esp_tls_cert_verify_flags,
                         event->error_handle->connect_return_code);
            }

            break;
        }


        default:
            break;
    }
}


// ============================================================
// MQTT START
// ============================================================

static void mqtt_start(void)
{
    ESP_LOGI(
        TAG,
        "MQTT Broker: %s",
        MQTT_BROKER_URI
    );


    esp_mqtt_client_config_t mqtt_cfg = {

        .broker.address.uri =
            MQTT_BROKER_URI,

        .broker.verification.crt_bundle_attach = esp_crt_bundle_attach,
        .broker.verification.skip_cert_common_name_check = false,

        .credentials.client_id =
            DEVICE_ID,

        .credentials.username = MQTT_USERNAME,
        .credentials.authentication.password = MQTT_PASSWORD,

        .session.last_will.topic = MQTT_TOPIC_STATUS,
        .session.last_will.msg = "{\"deviceId\":\"" DEVICE_ID "\",\"status\":\"OFFLINE\"}",
        .session.last_will.qos = 1,
        .session.last_will.retain = true,

        .session.keepalive =
            60,

        .network.reconnect_timeout_ms =
            5000,
        .network.disable_auto_reconnect = false
    };


    mqtt_client =
        esp_mqtt_client_init(
            &mqtt_cfg
        );

    if (mqtt_client == NULL) {
        ESP_LOGE(TAG, "Cannot initialize MQTT client");
        return;
    }


    ESP_ERROR_CHECK(
        esp_mqtt_client_register_event(
            mqtt_client,
            ESP_EVENT_ANY_ID,
            mqtt_event_handler,
            NULL
        )
    );


    ESP_ERROR_CHECK(
        esp_mqtt_client_start(
            mqtt_client
        )
    );
}


// ============================================================
// WIFI EVENT
// ============================================================

static void wifi_event_handler(
    void *arg,
    esp_event_base_t event_base,
    int32_t event_id,
    void *event_data
)
{
    if (
        event_base == WIFI_EVENT
        &&
        event_id
            == WIFI_EVENT_STA_START
    )
    {
        esp_wifi_connect();
    }


    else if (
        event_base == WIFI_EVENT
        &&
        event_id
            == WIFI_EVENT_STA_DISCONNECTED
    )
    {
        ESP_LOGW(
            TAG,
            "WiFi disconnected"
        );


        mqtt_connected =
            false;


        xEventGroupClearBits(wifi_event_group, WIFI_CONNECTED_BIT);
        esp_wifi_connect();
    }


    else if (
        event_base == IP_EVENT
        &&
        event_id
            == IP_EVENT_STA_GOT_IP
    )
    {
        ip_event_got_ip_t *event =
            event_data;


        ESP_LOGI(
            TAG,
            "================================"
        );

        ESP_LOGI(
            TAG,
            "WIFI CONNECTED"
        );


        ESP_LOGI(
            TAG,
            "ESP32 IP: " IPSTR,
            IP2STR(
                &event->ip_info.ip
            )
        );


        ESP_LOGI(
            TAG,
            "================================"
        );


        xEventGroupSetBits(
            wifi_event_group,
            WIFI_CONNECTED_BIT
        );
    }
}


// ============================================================
// WIFI INIT
// ============================================================

static void wifi_init(void)
{
    wifi_event_group =
        xEventGroupCreate();


    ESP_ERROR_CHECK(
        esp_netif_init()
    );


    ESP_ERROR_CHECK(
        esp_event_loop_create_default()
    );


    esp_netif_create_default_wifi_sta();


    wifi_init_config_t cfg =
        WIFI_INIT_CONFIG_DEFAULT();


    ESP_ERROR_CHECK(
        esp_wifi_init(
            &cfg
        )
    );


    ESP_ERROR_CHECK(
        esp_event_handler_register(
            WIFI_EVENT,
            ESP_EVENT_ANY_ID,
            wifi_event_handler,
            NULL
        )
    );


    ESP_ERROR_CHECK(
        esp_event_handler_register(
            IP_EVENT,
            IP_EVENT_STA_GOT_IP,
            wifi_event_handler,
            NULL
        )
    );


    wifi_config_t wifi_config = {0};


    strncpy(
        (char *)wifi_config.sta.ssid,
        WIFI_SSID,
        sizeof(
            wifi_config.sta.ssid
        ) - 1
    );


    strncpy(
        (char *)wifi_config.sta.password,
        WIFI_PASSWORD,
        sizeof(
            wifi_config.sta.password
        ) - 1
    );


    wifi_config.sta.threshold.authmode =
        WIFI_AUTH_WPA2_PSK;


    ESP_ERROR_CHECK(
        esp_wifi_set_mode(
            WIFI_MODE_STA
        )
    );


    ESP_ERROR_CHECK(
        esp_wifi_set_config(
            WIFI_IF_STA,
            &wifi_config
        )
    );


    ESP_ERROR_CHECK(
        esp_wifi_start()
    );


    ESP_LOGI(
        TAG,
        "Connecting WiFi: %s",
        WIFI_SSID
    );


    xEventGroupWaitBits(
        wifi_event_group,
        WIFI_CONNECTED_BIT,
        pdFALSE,
        pdTRUE,
        portMAX_DELAY
    );
}


// ============================================================
// GPIO INIT
// ============================================================

static void gpio_init_all(void)
{
    gpio_config_t outputs = {

        .pin_bit_mask =
            (1ULL << FAN_IN1)
            |
            (1ULL << FAN_IN2)
            |
            (1ULL << FAN_ENA)
            |
            (1ULL << LED_PIN),

        .mode =
            GPIO_MODE_OUTPUT,

        .pull_up_en =
            GPIO_PULLUP_DISABLE,

        .pull_down_en =
            GPIO_PULLDOWN_DISABLE,

        .intr_type =
            GPIO_INTR_DISABLE
    };


    ESP_ERROR_CHECK(
        gpio_config(
            &outputs
        )
    );


    gpio_config_t light_input = {

        .pin_bit_mask =
            (1ULL << LIGHT_SENSOR_PIN),

        .mode =
            GPIO_MODE_INPUT,

        .pull_up_en =
            GPIO_PULLUP_ENABLE,

        .pull_down_en =
            GPIO_PULLDOWN_DISABLE,

        .intr_type =
            GPIO_INTR_DISABLE
    };


    ESP_ERROR_CHECK(
        gpio_config(
            &light_input
        )
    );


    gpio_config_t dht_input = {

        .pin_bit_mask =
            (1ULL << DHT_PIN),

        .mode =
            GPIO_MODE_INPUT,

        .pull_up_en =
            GPIO_PULLUP_ENABLE,

        .pull_down_en =
            GPIO_PULLDOWN_DISABLE,

        .intr_type =
            GPIO_INTR_DISABLE
    };


    ESP_ERROR_CHECK(
        gpio_config(
            &dht_input
        )
    );


    fan_off();

    led_off();
}


// ============================================================
// TELEMETRY TASK
// ============================================================

static void telemetry_task(
    void *pvParameters
)
{
    // DHT startup
    vTaskDelay(
        pdMS_TO_TICKS(
            3000
        )
    );


    while (1)
    {
        float temperature =
            0.0f;

        float humidity =
            0.0f;


        bool ok =
            dht22_read(
                &temperature,
                &humidity
            );


        if (
            ok
        )
        {
            last_temperature =
                temperature;

            last_humidity =
                humidity;

            have_dht_data =
                true;
        }
        else
        {
            ESP_LOGW(
                TAG,
                "DHT read failed"
            );


            if (
                have_dht_data
            )
            {
                temperature =
                    last_temperature;

                humidity =
                    last_humidity;
            }
        }


        ESP_LOGI(
            TAG,
            "================================"
        );


        if (
            have_dht_data
        )
        {
            ESP_LOGI(
                TAG,
                "Temperature : %.1f C",
                temperature
            );


            ESP_LOGI(
                TAG,
                "Humidity    : %.1f %%",
                humidity
            );
        }


        ESP_LOGI(
            TAG,
            "Light Sensor: %s",
            light_sensor_active()
                ? "ACTIVE"
                : "INACTIVE"
        );


        ESP_LOGI(
            TAG,
            "Fan         : %s",
            fan_is_on
                ? "ON"
                : "OFF"
        );


        ESP_LOGI(
            TAG,
            "Light       : %s",
            led_is_on
                ? "ON"
                : "OFF"
        );


        ESP_LOGI(
            TAG,
            "================================"
        );


        if (
            have_dht_data
        )
        {
            mqtt_publish_telemetry(
                temperature,
                humidity
            );
        }


        vTaskDelay(
            pdMS_TO_TICKS(
                5000
            )
        );
    }
}


// ============================================================
// APP MAIN
// ============================================================

static bool secrets_configured(void)
{
    return strlen(WIFI_SSID) > 0 && strcmp(WIFI_SSID, "YOUR_WIFI_SSID") != 0
        && strlen(WIFI_PASSWORD) > 0 && strcmp(WIFI_PASSWORD, "YOUR_WIFI_PASSWORD") != 0
        && strlen(MQTT_USERNAME) > 0
        && strlen(MQTT_PASSWORD) > 0 && strcmp(MQTT_PASSWORD, "YOUR_MQTT_PASSWORD") != 0;
}

static void sync_time_for_tls(void)
{
    esp_sntp_config_t config = ESP_NETIF_SNTP_DEFAULT_CONFIG("pool.ntp.org");
    ESP_ERROR_CHECK(esp_netif_sntp_init(&config));
    while (esp_netif_sntp_sync_wait(pdMS_TO_TICKS(30000)) != ESP_OK) {
        ESP_LOGW(TAG, "Waiting for SNTP time before certificate verification; check WiFi/UDP 123");
    }
    ESP_LOGI(TAG, "Time synchronized; server certificate verification enabled");
}

void app_main(void)
{
    ESP_LOGI(
        TAG,
        "========================================"
    );

    ESP_LOGI(
        TAG,
        "FINAL PROJECT - SMART ROOM"
    );

    ESP_LOGI(
        TAG,
        "ESP32-C3 SUPER MINI"
    );

    ESP_LOGI(
        TAG,
        "========================================"
    );


    // NVS
    esp_err_t ret =
        nvs_flash_init();


    if (
        ret == ESP_ERR_NVS_NO_FREE_PAGES
        ||
        ret == ESP_ERR_NVS_NEW_VERSION_FOUND
    )
    {
        ESP_ERROR_CHECK(
            nvs_flash_erase()
        );


        ESP_ERROR_CHECK(
            nvs_flash_init()
        );
    }
    else
    {
        ESP_ERROR_CHECK(
            ret
        );
    }


    // GPIO
    gpio_init_all();

    if (!secrets_configured()) {
        ESP_LOGE(TAG, "Fill main/secrets.h, rebuild and flash; placeholder credentials are not used");
        return;
    }


    // WiFi
    wifi_init();

    sync_time_for_tls();


    // MQTT
    mqtt_start();


    // Telemetry task
    xTaskCreate(
        telemetry_task,
        "telemetry_task",
        4096,
        NULL,
        5,
        NULL
    );
}
