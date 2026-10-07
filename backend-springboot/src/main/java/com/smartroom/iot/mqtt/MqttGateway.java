package com.smartroom.iot.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroom.iot.dto.CommandRequest;
import com.smartroom.iot.exception.MqttUnavailableException;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MqttGateway implements CommandPublisher, MqttCallbackExtended {
    private static final Logger log = LoggerFactory.getLogger(MqttGateway.class);
    private static final String[] TOPICS = {
            "iot/smartroom/+/telemetry", "iot/smartroom/+/state", "iot/smartroom/+/status"};
    private final MqttAsyncClient client;
    private final MqttConnectOptions options;
    private final MqttIngress ingress;
    private final ObjectMapper mapper;
    private final long publishTimeoutMs;
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean subscribing = new AtomicBoolean();
    private volatile boolean subscribed;
    private volatile boolean closing;

    public MqttGateway(MqttIngress ingress, ObjectMapper mapper,
            @Value("${smartroom.mqtt.broker-url}") String uri,
            @Value("${smartroom.mqtt.client-id}") String clientId,
            @Value("${smartroom.mqtt.publish-timeout-ms}") long publishTimeoutMs,
            MqttConnectOptions options) throws MqttException {
        this.ingress = ingress;
        this.mapper = mapper;
        this.publishTimeoutMs = publishTimeoutMs;
        this.client = new MqttAsyncClient(uri, clientId, new MemoryPersistence());
        this.client.setCallback(this);
        this.options = options;
    }

    @Scheduled(fixedDelayString = "${smartroom.mqtt.reconnect-delay-ms}")
    public void maintainConnection() {
        if (closing) return;
        if (client.isConnected()) {
            if (!subscribed) subscribe();
            return;
        }
        if (!connecting.compareAndSet(false, true)) return;
        try {
            client.connect(options, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) { connecting.set(false); }
                @Override public void onFailure(IMqttToken token, Throwable error) {
                    connecting.set(false);
                    log.warn("MQTT connect failed; retry scheduled: {}", error.getMessage());
                }
            });
        } catch (MqttException ex) {
            connecting.set(false);
            log.warn("MQTT connect failed; retry scheduled: {}", ex.getMessage());
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        connecting.set(false);
        subscribed = false;
        log.info("MQTT connected to {} as {}", serverURI, client.getClientId());
        subscribe();
    }

    private void subscribe() {
        if (closing || !subscribing.compareAndSet(false, true)) return;
        try {
            client.subscribe(TOPICS, new int[]{1, 1, 1}, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) {
                    subscribing.set(false);
                    subscribed = Arrays.stream(token.getGrantedQos()).noneMatch(qos -> qos == 128);
                    if (subscribed) log.info("MQTT subscriptions confirmed: {}", Arrays.toString(TOPICS));
                    else log.warn("MQTT subscription refused; retry scheduled");
                }
                @Override public void onFailure(IMqttToken token, Throwable error) {
                    subscribing.set(false);
                    log.warn("MQTT subscribe failed; retry scheduled: {}", error.getMessage());
                }
            });
        } catch (MqttException ex) {
            subscribing.set(false);
            log.warn("MQTT subscribe failed; retry scheduled: {}", ex.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        subscribed = false;
        subscribing.set(false);
        connecting.set(false);
        if (!closing) log.warn("MQTT connection lost; reconnect scheduled: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            ingress.receive(topic, message.getPayload());
        } catch (Exception ex) {
            // A bad message must not tear down the MQTT connection or stop processing later messages.
            log.warn("MQTT message rejected on {}: {}", topic, ex.getMessage());
        }
    }

    @Override public void deliveryComplete(IMqttDeliveryToken token) { }

    @Override
    public void publish(String deviceId, CommandRequest command) {
        if (closing || !client.isConnected()) throw new MqttUnavailableException("MQTT disconnected");
        try {
            byte[] bytes = mapper.writeValueAsBytes(command);
            // Commands must never be retained; QoS 1 waits for broker PUBACK.
            client.publish("iot/smartroom/" + deviceId + "/command", bytes, 1, false)
                    .waitForCompletion(publishTimeoutMs);
        } catch (MqttException | JsonProcessingException ex) {
            throw new MqttUnavailableException("MQTT publish failed or timed out", ex);
        }
    }

    @PreDestroy
    public void close() {
        closing = true;
        try {
            if (client.isConnected()) client.disconnect().waitForCompletion(2000);
            client.close(true);
        } catch (MqttException ex) {
            log.warn("MQTT shutdown: {}", ex.getMessage());
        }
    }
}
