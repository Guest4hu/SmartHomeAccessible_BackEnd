package com.usjt.sistema_automatizado.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.dto.request.EventRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.model.enums.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MqttMessageMapperTest {

    private MqttMessageMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MqttMessageMapper(new ObjectMapper());
    }

    @Test
    void toTelemetryRequest_DeveConverterPayloadJsonComSucesso() throws Exception {
        // Arrange
        String payload = "{\"v\":2,\"temperature\":24.5,\"humidity\":55.0,\"luminosity\":350.0}";
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/telemetry", "esp32-01", payload);

        // Act
        TelemetryRequest request = mapper.toTelemetryRequest(envelope);

        // Assert
        assertNotNull(request);
        assertEquals(2, request.v());
        assertEquals("esp32-01", request.deviceId());
        assertEquals(24.5, request.temperature());
        assertEquals(55.0, request.humidity());
        assertEquals(350.0, request.luminosity());
        assertNotNull(request.ts());
    }

    @Test
    void toTelemetryRequest_DeveConverterPayloadComCamposOpcionaisNulos() throws Exception {
        // Arrange
        String payload = "{}";
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/telemetry", "esp32-01", payload);

        // Act
        TelemetryRequest request = mapper.toTelemetryRequest(envelope);

        // Assert
        assertNotNull(request);
        assertEquals(1, request.v()); // Default v = 1
        assertEquals("esp32-01", request.deviceId());
        assertNull(request.temperature());
        assertNull(request.humidity());
        assertNull(request.luminosity());
    }

    @Test
    void toEventRequest_DeveConverterPayloadJsonComSucesso() throws Exception {
        // Arrange
        String payload = "{\"v\":1,\"type\":\"DOORBELL\"}";
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/event", "esp32-01", payload);

        // Act
        EventRequest request = mapper.toEventRequest(envelope);

        // Assert
        assertNotNull(request);
        assertEquals(1, request.v());
        assertEquals("esp32-01", request.deviceId());
        assertEquals(EventType.DOORBELL, request.type());
        assertNotNull(request.ts());
    }

    @Test
    void toEventType_DeveExtrairTipoOuDefault() throws Exception {
        // Arrange
        MqttEnvelope env1 = new MqttEnvelope("t", "d", "{\"type\":\"PRESENCE_DETECTED\"}");
        MqttEnvelope env2 = new MqttEnvelope("t", "d", "{}");

        // Act & Assert
        assertEquals("PRESENCE_DETECTED", mapper.toEventType(env1));
        assertEquals("UNKNOWN_EVENT", mapper.toEventType(env2));
    }

    @Test
    void extrairCorrelationId_DeveRetornarIdQuandoExistir() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("t", "d", "{\"correlationId\":\"abc-123\"}");

        // Act
        String correlationId = mapper.extrairCorrelationId(envelope);

        // Assert
        assertEquals("abc-123", correlationId);
    }

    @Test
    void extrairCorrelationId_DeveRetornarVazioQuandoNaoExistir() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("t", "d", "{\"type\":\"DOORBELL\"}");

        // Act
        String correlationId = mapper.extrairCorrelationId(envelope);

        // Assert
        assertEquals("", correlationId);
    }
}
