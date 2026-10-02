package com.usjt.sistema_automatizado.service.mqtt;

import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.mapper.MqttMessageMapper;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;
import com.usjt.sistema_automatizado.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryMqttHandlerTest {

    @Mock
    private TelemetryService telemetryService;

    @Mock
    private MqttMessageMapper mqttMapper;

    @InjectMocks
    private TelemetryMqttHandler handler;

    @Test
    void supports_DeveRetornarTrueApenasParaTelemetry() {
        assertTrue(handler.supports(MqttMessageType.TELEMETRY));
        assertFalse(handler.supports(MqttMessageType.EVENT));
        assertFalse(handler.supports(MqttMessageType.STATUS));
        assertFalse(handler.supports(MqttMessageType.COMMAND));
        assertFalse(handler.supports(MqttMessageType.UNKNOWN));
    }

    @Test
    void handle_DeveMapearESalvarTelemetria() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/telemetry", "esp32-01", "{\"temperature\":25.0}");
        TelemetryRequest request = new TelemetryRequest(1, "esp32-01", LocalDateTime.now(), 25.0, 60.0, 400.0);

        when(mqttMapper.toTelemetryRequest(envelope)).thenReturn(request);

        // Act
        handler.handle(envelope);

        // Assert
        verify(mqttMapper, times(1)).toTelemetryRequest(envelope);
        verify(telemetryService, times(1)).saveTelemetry(request);
    }

    @Test
    void handle_DevePropagarExcecao_QuandoMapperFalhar() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/telemetry", "esp32-01", "invalid-json");
        when(mqttMapper.toTelemetryRequest(envelope)).thenThrow(new IllegalArgumentException("JSON inválido"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> handler.handle(envelope));
        verify(telemetryService, never()).saveTelemetry(any());
    }
}
