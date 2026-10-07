package com.smartroom.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class MqttGatewayTest {
    @Test
    void reconnectUsesSameOptionsAndResubscribesToExistingTopics() throws Exception {
        try (var construction = mockConstruction(MqttAsyncClient.class)) {
            var options = MqttConnectionConfig.createOptions("tcp://localhost:1883", "", "", false, "");
            var gateway = new MqttGateway(mock(MqttIngress.class), new ObjectMapper(),
                    "tcp://localhost:1883", "test-client", 5000, options);
            var client = construction.constructed().get(0);
            try {
                gateway.maintainConnection();
                verify(client).connect(same(options), isNull(), any(IMqttActionListener.class));
                when(client.isConnected()).thenReturn(true);
                gateway.connectComplete(false, "tcp://localhost:1883");
                var listener = ArgumentCaptor.forClass(IMqttActionListener.class);
                verify(client).subscribe(eq(new String[]{"iot/smartroom/+/telemetry", "iot/smartroom/+/state", "iot/smartroom/+/status"}),
                        eq(new int[]{1, 1, 1}), isNull(), listener.capture());
                var token = mock(IMqttToken.class);
                when(token.getGrantedQos()).thenReturn(new int[]{1, 1, 1});
                listener.getValue().onSuccess(token);
                gateway.maintainConnection();
                verify(client, times(1)).subscribe(any(String[].class), any(int[].class), isNull(), any());
                gateway.connectionLost(new IllegalStateException("test disconnect"));
                when(client.isConnected()).thenReturn(false);
                gateway.maintainConnection();
                verify(client, times(2)).connect(same(options), isNull(), any(IMqttActionListener.class));
                gateway.connectComplete(true, "tcp://localhost:1883");
                verify(client, times(2)).subscribe(any(String[].class), eq(new int[]{1, 1, 1}), isNull(), any());
                assertThat(options.isAutomaticReconnect()).isFalse();
            } finally { gateway.close(); }
        }
    }
}
