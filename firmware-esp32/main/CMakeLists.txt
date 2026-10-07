idf_component_register(
    SRCS
        "hello_world_main.c"

    INCLUDE_DIRS
        "."

    REQUIRES
        driver
        esp_timer
        esp_wifi
        esp_event
        esp_netif
        nvs_flash
        mqtt
        json
        mbedtls
)
