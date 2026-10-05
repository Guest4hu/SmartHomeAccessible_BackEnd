package com.usjt.sistema_automatizado.service.mqtt;

import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;
import com.usjt.sistema_automatizado.service.DeviceService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusMqttHandlerTest {

    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private StatusMqttHandler handler;

    @Test
    void supports_DeveRetornarTrueApenasParaStatus() {
        assertTrue(handler.supports(MqttMessageType.STATUS));
        assertFalse(handler.supports(MqttMessageType.TELEMETRY));
        assertFalse(handler.supports(MqttMessageType.EVENT));
        assertFalse(handler.supports(MqttMessageType.COMMAND));
        assertFalse(handler.supports(MqttMessageType.UNKNOWN));
    }

    @Test
    void handle_DeveAtualizarStatusParaOnline_QuandoPayloadOnline() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/status", "esp32-01", "ONLINE");

        // Act
        handler.handle(envelope);

        // Assert
        verify(deviceService, times(1)).updateDeviceStatus("esp32-01", DeviceStatus.ONLINE);
    }

    @Test
    void handle_DeveAtualizarStatusParaOffline_QuandoPayloadOffline() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/status", "esp32-01", "OFFLINE");

        // Act
        handler.handle(envelope);

        // Assert
        verify(deviceService, times(1)).updateDeviceStatus("esp32-01", DeviceStatus.OFFLINE);
    }

    @Test
    void handle_DeveTratarTextoComEspacosEMinusculas() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/status", "esp32-01", "  online  ");

        // Act
        handler.handle(envelope);

        // Assert
        verify(deviceService, times(1)).updateDeviceStatus("esp32-01", DeviceStatus.ONLINE);
    }

    @Test
    void handle_NaoDeveAtualizar_QuandoPayloadInvalido() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/status", "esp32-01", "DESCONHECIDO");

        // Act
        handler.handle(envelope);

        // Assert
        verify(deviceService, never()).updateDeviceStatus(anyString(), any());
    }

    @Test
    void handle_NaoDevePropagarExcecao_QuandoDeviceServiceLancarEntityNotFoundException() {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-inexistente/status", "esp32-inexistente", "ONLINE");
        doThrow(new EntityNotFoundException("Dispositivo não encontrado"))
                .when(deviceService).updateDeviceStatus("esp32-inexistente", DeviceStatus.ONLINE);

        // Act & Assert
        assertDoesNotThrow(() -> handler.handle(envelope));
    }
}
