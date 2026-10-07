package com.smartroom.iot.service;

import com.smartroom.iot.dto.CommandRequest;
import com.smartroom.iot.entity.Command;
import com.smartroom.iot.exception.MqttUnavailableException;
import com.smartroom.iot.mqtt.CommandPublisher;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandServiceTest {
    private final CommandRecordService records = mock(CommandRecordService.class);
    private final CommandPublisher publisher = mock(CommandPublisher.class);
    private final CommandService service = new CommandService(records, publisher);

    @Test
    void pendingIsCommittedBeforePublishAndSentIsSavedAfterPublish() {
        CommandRequest request = new CommandRequest("fan", "ON");
        Command command = mock(Command.class);
        when(command.getId()).thenReturn(12L);
        when(records.pending("d", request)).thenReturn(command);
        when(records.finish(12L, "SENT")).thenReturn(command);
        assertThat(service.send("d", request)).isSameAs(command);
        var order = inOrder(records, publisher);
        order.verify(records).pending("d", request);
        order.verify(publisher).publish("d", request);
        order.verify(records).finish(12L, "SENT");
    }

    @Test
    void disconnectedBrokerStillPersistsFailedCommand() {
        CommandRequest request = new CommandRequest("light", "OFF");
        Command command = mock(Command.class);
        when(command.getId()).thenReturn(13L);
        when(records.pending("d", request)).thenReturn(command);
        doThrow(new MqttUnavailableException("disconnected")).when(publisher).publish("d", request);
        when(records.finish(13L, "FAILED")).thenReturn(command);
        assertThat(service.send("d", request)).isSameAs(command);
        verify(records).finish(13L, "FAILED");
        verify(records, never()).finish(anyLong(), eq("SENT"));
    }
}
