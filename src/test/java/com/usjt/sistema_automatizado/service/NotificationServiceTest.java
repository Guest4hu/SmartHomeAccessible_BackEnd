package com.usjt.sistema_automatizado.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(objectMapper);
    }

    @Test
    void subscribe_DeveAdicionarEmitterERetornarConexao() {
        assertEquals(0, notificationService.getActiveEmittersCount());

        SseEmitter emitter = notificationService.subscribe();

        assertNotNull(emitter);
        assertEquals(1, notificationService.getActiveEmittersCount());
    }

    @Test
    void dispatchEvent_DeveSerializarPayloadEEnviarComNomePadraoBellRing() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        notificationService.addEmitter(emitter);

        when(objectMapper.writeValueAsString(any(NotificationService.NotificationPayload.class)))
                .thenReturn("{\"deviceId\":\"esp32-01\",\"event\":\"DOORBELL\"}");

        notificationService.dispatchEvent("esp32-01", "DOORBELL");

        ArgumentCaptor<NotificationService.NotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(NotificationService.NotificationPayload.class);
        verify(objectMapper, times(1)).writeValueAsString(payloadCaptor.capture());

        NotificationService.NotificationPayload capturedPayload = payloadCaptor.getValue();
        assertEquals("esp32-01", capturedPayload.deviceId());
        assertEquals("DOORBELL", capturedPayload.event());
        assertNotNull(capturedPayload.timestamp());

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertEquals(1, notificationService.getActiveEmittersCount());
    }

    @Test
    void dispatchEvent_DevePermitirNomeCustomizadoDeEvento() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        notificationService.addEmitter(emitter);

        when(objectMapper.writeValueAsString(any(NotificationService.NotificationPayload.class)))
                .thenReturn("{\"deviceId\":\"esp32-02\",\"event\":\"COMMAND_SUCCESS\"}");

        notificationService.dispatchEvent("custom-event", "esp32-02", "COMMAND_SUCCESS");

        verify(objectMapper, times(1)).writeValueAsString(any(NotificationService.NotificationPayload.class));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void dispatchEvent_DeveRemoverEmitter_QuandoOcorrerErroDeEnvio() throws Exception {
        SseEmitter failingEmitter = mock(SseEmitter.class);
        doThrow(new IOException("Broken pipe")).when(failingEmitter).send(any(SseEmitter.SseEventBuilder.class));

        notificationService.addEmitter(failingEmitter);
        assertEquals(1, notificationService.getActiveEmittersCount());

        when(objectMapper.writeValueAsString(any(NotificationService.NotificationPayload.class)))
                .thenReturn("{\"deviceId\":\"esp32-01\",\"event\":\"DOORBELL\"}");

        notificationService.dispatchEvent("esp32-01", "DOORBELL");

        assertEquals(0, notificationService.getActiveEmittersCount());
    }

    @Test
    void dispatchEvent_NaoDevePropagarExcecao_QuandoSerializacaoJsonFalhar() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        notificationService.addEmitter(emitter);

        when(objectMapper.writeValueAsString(any(NotificationService.NotificationPayload.class)))
                .thenThrow(new JsonProcessingException("Serialization failed") {});

        assertDoesNotThrow(() -> notificationService.dispatchEvent("esp32-01", "DOORBELL"));

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        assertEquals(1, notificationService.getActiveEmittersCount());
    }

    @Test
    void notificationPayload_ConstrutorSecundarioDeveInicializarTimestamp() {
        NotificationService.NotificationPayload payload =
                new NotificationService.NotificationPayload("esp32-03", "ALERT");

        assertEquals("esp32-03", payload.deviceId());
        assertEquals("ALERT", payload.event());
        assertNotNull(payload.timestamp());
    }
}
