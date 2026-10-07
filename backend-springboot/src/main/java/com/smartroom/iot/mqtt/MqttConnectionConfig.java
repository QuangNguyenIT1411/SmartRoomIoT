package com.smartroom.iot.mqtt;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
public class MqttConnectionConfig {
    @Bean
    MqttConnectOptions mqttConnectOptions(
            @Value("${smartroom.mqtt.broker-url}") String brokerUrl,
            @Value("${smartroom.mqtt.username}") String username,
            @Value("${smartroom.mqtt.password}") String password,
            @Value("${smartroom.mqtt.tls-enabled}") boolean tlsEnabled,
            @Value("${smartroom.mqtt.ca-path}") String caPath) {
        return createOptions(brokerUrl, username, password, tlsEnabled, caPath);
    }

    static MqttConnectOptions createOptions(String brokerUrl, String username, String password,
            boolean tlsEnabled, String caPath) {
        validateBroker(brokerUrl, tlsEnabled);
        if (username.isEmpty() && !password.isEmpty()) {
            throw new IllegalArgumentException("MQTT_PASSWORD requires MQTT_USERNAME");
        }
        if (!tlsEnabled && !caPath.isBlank()) {
            throw new IllegalArgumentException("MQTT_CA_PATH requires MQTT_TLS_ENABLED=true");
        }
        var options = new MqttConnectOptions();
        options.setCleanSession(true);
        // The gateway's scheduled retry handles initial failure and reconnect.
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(5);
        options.setKeepAliveInterval(20);
        if (!username.isEmpty()) options.setUserName(username);
        // Do not trim credentials: whitespace can be part of a valid password.
        if (!password.isEmpty()) options.setPassword(password.toCharArray());
        options.setHttpsHostnameVerificationEnabled(true);
        if (tlsEnabled) {
            try {
                options.setSocketFactory(caPath.isBlank()
                        ? SSLContext.getDefault().getSocketFactory()
                        : customCaContext(caPath).getSocketFactory());
            } catch (Exception ex) {
                // Do not echo credentials, URI, certificate contents or local paths.
                throw new IllegalArgumentException("MQTT TLS initialization failed; check MQTT_CA_PATH and JVM trust configuration");
            }
        }
        return options;
    }

    private static void validateBroker(String brokerUrl, boolean tlsEnabled) {
        try {
            var uri = URI.create(brokerUrl);
            if (!(tlsEnabled ? "ssl" : "tcp").equals(uri.getScheme())
                    || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("MQTT_BROKER_URL must be tcp://host:port with TLS disabled or ssl://host:port with TLS enabled; credentials belong in separate variables");
        }
    }

    private static SSLContext customCaContext(String caPath) throws Exception {
        var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        try (InputStream input = openCa(caPath)) {
            var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
            if (certificates.isEmpty()) throw new IllegalArgumentException("Empty CA file");
            int index = 0;
            for (var certificate : certificates) {
                if (!(certificate instanceof X509Certificate ca) || ca.getBasicConstraints() < 0) {
                    throw new IllegalArgumentException("Expected CA certificates");
                }
                ca.checkValidity();
                trustStore.setCertificateEntry("mqtt-ca-" + index++, ca);
            }
        }
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trustStore);
        var context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        return context;
    }

    private static InputStream openCa(String caPath) throws Exception {
        if (caPath.startsWith("classpath:")) {
            return new ClassPathResource(caPath.substring("classpath:".length())).getInputStream();
        }
        return Files.newInputStream(caPath.startsWith("file:") ? Path.of(URI.create(caPath)) : Path.of(caPath));
    }
}
