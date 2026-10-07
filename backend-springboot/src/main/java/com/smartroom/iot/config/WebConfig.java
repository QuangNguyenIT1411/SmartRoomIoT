package com.smartroom.iot.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String corsOrigin;

    public WebConfig(@Value("${smartroom.cors-origin}") String corsOrigin) {
        this.corsOrigin = corsOrigin;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(corsOrigin)
                .allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("Content-Type");
    }
}
