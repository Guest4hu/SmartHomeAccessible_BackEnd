package com.usjt.sistema_automatizado.service.mqtt;

import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.dto.request.EventRequest;
import com.usjt.sistema_automatizado.mapper.MqttMessageMapper;
import com.usjt.sistema_automatizado.model.enums.CommandDeliveryStatus;
import com.usjt.sistema_automatizado.model.enums.EventType;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.service.CommandAckService;
import com.usjt.sistema_automatizado.service.EventService;
import com.usjt.sistema_automatizado.service.NotificationService;
import com.usjt.sistema_automatizado.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventMqttHandlerTest {

    @Mock
    private EventService eventService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MqttMessageMapper mqttMapper;

    @Mock
    private CommandAckService commandAckService;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private EventMqttHandler handler;

    @Test
    void supports_DeveRetornarTrueApenasParaEvent() {
        assertTrue(handler.supports(MqttMessageType.EVENT));
        assertFalse(handler.supports(MqttMessageType.TELEMETRY));
        assertFalse(handler.supports(MqttMessageType.STATUS));
        assertFalse(handler.supports(MqttMessageType.COMMAND));
        assertFalse(handler.supports(MqttMessageType.UNKNOWN));
    }

    @Test
    void handle_DeveProcessarEventoFisico_SemResolverAck_EDispararPushQuandoDoorbell() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/event", "esp32-01", "{\"type\":\"DOORBELL\"}");
        EventRequest request = new EventRequest(1, "esp32-01", LocalDateTime.now(), EventType.DOORBELL);

        when(mqttMapper.toEventRequest(envelope)).thenReturn(request);
        when(deviceRepository.findHomeIdByExternalId("esp32-01")).thenReturn(Optional.of(10L));

        // Act
        handler.handle(envelope);

        // Assert
        verify(commandAckService, never()).resolverAck(anyString(), any(CommandDeliveryStatus.class));
        verify(commandAckService, never()).resolverAck(anyString(), anyString());
        verify(eventService, times(1)).createEvent(request);
        verify(notificationService, times(1)).dispatchEvent("esp32-01", "DOORBELL");
        verify(pushNotificationService, times(1)).sendNotificationToHome(
                eq(10L),
                eq("Campainha Acionada"),
                contains("esp32-01"),
                anyMap()
        );
    }

    @Test
    void handle_NaoDeveDispararPushNotification_QuandoEventoNaoForDoorbell() throws Exception {
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/event", "esp32-01", "{\"type\":\"PRESENCE_DETECTED\"}");
        EventRequest request = new EventRequest(1, "esp32-01", LocalDateTime.now(), EventType.PRESENCE_DETECTED);

        when(mqttMapper.toEventRequest(envelope)).thenReturn(request);

        handler.handle(envelope);

        verify(eventService, times(1)).createEvent(request);
        verify(notificationService, times(1)).dispatchEvent("esp32-01", "PRESENCE_DETECTED");
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void handle_DeveResolverAckComDelivered_QuandoCommandSuccess() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/event", "esp32-01", "{\"type\":\"COMMAND_SUCCESS\",\"correlationId\":\"uuid-123\"}");
        EventRequest request = new EventRequest(1, "esp32-01", LocalDateTime.now(), EventType.COMMAND_SUCCESS);

        when(mqttMapper.toEventRequest(envelope)).thenReturn(request);
        when(mqttMapper.extrairCorrelationId(envelope)).thenReturn("uuid-123");

        // Act
        handler.handle(envelope);

        // Assert
        verify(commandAckService, times(1)).resolverAck("esp32-01", "uuid-123", CommandDeliveryStatus.DELIVERED);
        verify(eventService, times(1)).createEvent(request);
        verify(notificationService, times(1)).dispatchEvent("esp32-01", "COMMAND_SUCCESS");
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void handle_DeveResolverAckComFailed_QuandoCommandFailed() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/event", "esp32-01", "{\"type\":\"COMMAND_FAILED\",\"correlationId\":\"uuid-456\"}");
        EventRequest request = new EventRequest(1, "esp32-01", LocalDateTime.now(), EventType.COMMAND_FAILED);

        when(mqttMapper.toEventRequest(envelope)).thenReturn(request);
        when(mqttMapper.extrairCorrelationId(envelope)).thenReturn("uuid-456");

        // Act
        handler.handle(envelope);

        // Assert
        verify(commandAckService, times(1)).resolverAck("esp32-01", "uuid-456", CommandDeliveryStatus.FAILED);
        verify(eventService, times(1)).createEvent(request);
        verify(notificationService, times(1)).dispatchEvent("esp32-01", "COMMAND_FAILED");
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void handle_DevePropagarExcecao_QuandoMapeamentoFalhar() throws Exception {
        // Arrange
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-01/event", "esp32-01", "bad-payload");
        when(mqttMapper.toEventRequest(envelope)).thenThrow(new RuntimeException("Parser error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> handler.handle(envelope));
        verify(commandAckService, never()).resolverAck(anyString(), any(CommandDeliveryStatus.class));
        verify(commandAckService, never()).resolverAck(anyString(), anyString());
        verify(eventService, never()).createEvent(any());
        verify(notificationService, never()).dispatchEvent(anyString(), anyString());
        verifyNoInteractions(pushNotificationService);
    }
}
