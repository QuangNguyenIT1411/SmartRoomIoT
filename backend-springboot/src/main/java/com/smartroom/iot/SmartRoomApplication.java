package com.smartroom.iot;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartRoomApplication {
    public static void main(String[] args) {
        // pgjdbc sends the JVM time zone at connect time. Use UTC consistently with
        // the existing TIMESTAMP schema, including hosts with legacy zone aliases.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SmartRoomApplication.class, args);
    }
}
