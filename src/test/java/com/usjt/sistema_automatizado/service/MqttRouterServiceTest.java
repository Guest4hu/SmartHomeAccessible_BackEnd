package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;
import com.usjt.sistema_automatizado.service.mqtt.MqttMessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttRouterServiceTest {

    @Mock
    private MqttMessageHandler telemetryHandler;

    @Mock
    private MqttMessageHandler eventHandler;

    @Mock
    private MqttMessageHandler statusHandler;

    private MqttRouterService routerService;

    @BeforeEach
    void setUp() {
        lenient().when(telemetryHandler.supports(MqttMessageType.TELEMETRY)).thenReturn(true);
        lenient().when(eventHandler.supports(MqttMessageType.EVENT)).thenReturn(true);
        lenient().when(statusHandler.supports(MqttMessageType.STATUS)).thenReturn(true);

        routerService = new MqttRouterService(List.of(telemetryHandler, eventHandler, statusHandler));
    }

    @Test
    void routeMessage_DeveDespacharParaHandlerCorreto_QuandoTelemetry() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/telemetry", "esp32-01", "{\"temperature\":25.0}");

        // Act
        routerService.routeMessage(envelope);

        // Assert
        verify(telemetryHandler, times(1)).handle(envelope);
        verify(eventHandler, never()).handle(any());
        verify(statusHandler, never()).handle(any());
    }

    @Test
    void routeMessage_DeveDespacharParaHandlerCorreto_QuandoStatusComPayloadTexto() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/status", "esp32-01", "ONLINE");

        // Act
        routerService.routeMessage(envelope);

        // Assert
        verify(statusHandler, times(1)).handle(envelope);
        verify(telemetryHandler, never()).handle(any());
        verify(eventHandler, never()).handle(any());
    }

    @Test
    void routeMessage_DeveIgnorarPayloadNaoJson_ParaTelemetry() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/telemetry", "esp32-01", "ONLINE");

        // Act
        routerService.routeMessage(envelope);

        // Assert
        verify(telemetryHandler, never()).handle(any());
    }

    @Test
    void routeMessage_DeveIgnorarPayloadNaoJson_ParaEvent() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/event", "esp32-01", "DOORBELL_PRESSED");

        // Act
        routerService.routeMessage(envelope);

        // Assert
        verify(eventHandler, never()).handle(any());
    }

    @Test
    void routeMessage_DeveIgnorarQuandoTopicoCommand() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/cmd", "esp32-01", "{\"action\":\"FAN_ON\"}");

        // Act
        routerService.routeMessage(envelope);

        // Assert
        verify(telemetryHandler, never()).handle(any());
        verify(eventHandler, never()).handle(any());
        verify(statusHandler, never()).handle(any());
    }

    @Test
    void routeMessage_DeveIgnorarQuandoTopicoDesconhecido() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/outro", "esp32-01", "{\"foo\":\"bar\"}");

        // Act
        routerService.routeMessage(envelope);

        // Assert
        verify(telemetryHandler, never()).handle(any());
        verify(eventHandler, never()).handle(any());
        verify(statusHandler, never()).handle(any());
    }

    @Test
    void routeMessage_DeveTratarEnvelopeOuPayloadNulo_SemLancarExcecao() {
        assertDoesNotThrow(() -> routerService.routeMessage(null));
        assertDoesNotThrow(() -> routerService.routeMessage(new MqttEnvelope("topic", "device", null)));
    }

    @Test
    void routeMessage_DeveCapturarExcecaoDoHandler_SemPropagar() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/status", "esp32-01", "ONLINE");
        doThrow(new RuntimeException("Database error")).when(statusHandler).handle(envelope);

        // Act & Assert
        assertDoesNotThrow(() -> routerService.routeMessage(envelope));
    }
}
