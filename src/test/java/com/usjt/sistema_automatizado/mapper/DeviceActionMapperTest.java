package com.usjt.sistema_automatizado.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usjt.sistema_automatizado.dto.request.CommandRequest;
import com.usjt.sistema_automatizado.model.enums.CommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceActionMapperTest {

    private ObjectMapper objectMapper;
    private DeviceActionMapper mapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mapper = new DeviceActionMapper(objectMapper);
    }

    @Test
    void toCommandJson_DeveMapearTurnOnParaFanOn() throws Exception {
        CommandRequest request = new CommandRequest(CommandType.TURN_ON);
        String correlationId = "corr-111";

        String json = mapper.toCommandJson(request, correlationId);

        JsonNode node = objectMapper.readTree(json);
        assertEquals("FAN_ON", node.get("action").asText());
        assertEquals("corr-111", node.get("correlationId").asText());
    }

    @Test
    void toCommandJson_DeveMapearTurnOffParaFanOff() throws Exception {
        CommandRequest request = new CommandRequest(CommandType.TURN_OFF);
        String correlationId = "corr-222";

        String json = mapper.toCommandJson(request, correlationId);

        JsonNode node = objectMapper.readTree(json);
        assertEquals("FAN_OFF", node.get("action").asText());
        assertEquals("corr-222", node.get("correlationId").asText());
    }

    @Test
    void toCommandJson_DeveMapearBlinkLedParaBlinkLed() throws Exception {
        CommandRequest request = new CommandRequest(CommandType.BLINK_LED);
        String correlationId = "corr-333";

        String json = mapper.toCommandJson(request, correlationId);

        JsonNode node = objectMapper.readTree(json);
        assertEquals("BLINK_LED", node.get("action").asText());
        assertEquals("corr-333", node.get("correlationId").asText());
    }

    @Test
    void toCommandJson_DeveMapearSetValueParaNomePadraoDoEnum() throws Exception {
        CommandRequest request = new CommandRequest(CommandType.SET_VALUE);
        String correlationId = "corr-444";

        String json = mapper.toCommandJson(request, correlationId);

        JsonNode node = objectMapper.readTree(json);
        assertEquals("SET_VALUE", node.get("action").asText());
        assertEquals("corr-444", node.get("correlationId").asText());
    }

    @Test
    void toCommandJson_DeveLancarRuntimeException_QuandoFalharSerializacaoJson() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Simulated JSON error") {});

        DeviceActionMapper errorMapper = new DeviceActionMapper(failingMapper);
        CommandRequest request = new CommandRequest(CommandType.TURN_ON);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> errorMapper.toCommandJson(request, "corr-err")
        );

        assertTrue(exception.getMessage().contains("Falha ao serializar payload"));
    }
}
