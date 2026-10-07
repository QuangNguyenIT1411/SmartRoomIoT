package com.smartroom.iot.controller;

import com.smartroom.iot.entity.Command;
import com.smartroom.iot.exception.*;
import com.smartroom.iot.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(properties = "smartroom.cors-origin=http://localhost:5173", controllers = DeviceController.class)
class DeviceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DeviceQueryService query;
    @MockitoBean CommandService commands;

    @Test
    void missingDeviceIsJson404() throws Exception {
        when(query.device("missing")).thenThrow(new NotFoundException("Device not found: missing"));
        mvc.perform(get("/api/devices/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.path").value("/api/devices/missing"));
    }

    @Test
    void rejectsInvalidCommandAndMalformedJsonWithoutPublishing() throws Exception {
        mvc.perform(post("/api/devices/d/commands").contentType(MediaType.APPLICATION_JSON)
                .content("{\"device\":\"heater\",\"action\":\"ON\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/devices/d/commands").contentType(MediaType.APPLICATION_JSON)
                .content("{broken")).andExpect(status().isBadRequest());
        verifyNoInteractions(commands);
    }

    @Test
    void boundsHistoryLimitAndRejectsNonNumbers() throws Exception {
        for (String limit : new String[]{"0", "1001", "abc"}) {
            mvc.perform(get("/api/devices/d/telemetry").param("limit", limit))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(query);
    }

    @Test
    void databaseFailureIsJson503() throws Exception {
        when(query.devices()).thenThrow(new DataAccessResourceFailureException("test unavailable"));
        mvc.perform(get("/api/devices")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Database temporarily unavailable"));
    }

    @Test
    void sentCommandReturns201AndFailedCommandReturns503() throws Exception {
        Command command = new Command();
        command.setStatus("SENT");
        when(commands.send(eq("d"), any())).thenReturn(command);
        mvc.perform(post("/api/devices/d/commands").contentType(MediaType.APPLICATION_JSON)
                .content("{\"device\":\"fan\",\"action\":\"ON\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SENT"));
        command.setStatus("FAILED");
        mvc.perform(post("/api/devices/d/commands").contentType(MediaType.APPLICATION_JSON)
                .content("{\"device\":\"fan\",\"action\":\"ON\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void allowsFrontendCorsPreflight() throws Exception {
        mvc.perform(options("/api/devices/d/commands").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
