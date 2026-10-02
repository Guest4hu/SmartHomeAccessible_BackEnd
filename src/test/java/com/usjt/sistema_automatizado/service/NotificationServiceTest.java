package com.usjt.sistema_automatizado.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private HomeMemberRepository homeMemberRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(objectMapper, deviceRepository, homeMemberRepository);
    }

    @Test
    void subscribe_DeveAdicionarEmitterParaOUsuarioERetornarConexao() {
        assertEquals(0, notificationService.getActiveEmittersCount());

        SseEmitter emitter = notificationService.subscribe(1L);

        assertNotNull(emitter);
        assertEquals(1, notificationService.getActiveEmittersCount());
        assertEquals(1, notificationService.getActiveEmittersCountForUser(1L));
    }

    @Test
    void subscribe_DeveLancarExcecao_QuandoUserIdForNulo() {
        assertThrows(IllegalArgumentException.class, () -> notificationService.subscribe(null));
    }

    @Test
    void dispatchEvent_DeveEnviarApenasParaMembrosDaCasaDoDispositivo() throws Exception {
        Long homeId = 10L;
        String deviceId = "esp32-01";
        Long memberUserId = 1L;
        Long otherUserId = 2L;

        SseEmitter memberEmitter = mock(SseEmitter.class);
        SseEmitter otherEmitter = mock(SseEmitter.class);

        notificationService.addEmitter(memberUserId, memberEmitter);
        notificationService.addEmitter(otherUserId, otherEmitter);

        when(deviceRepository.findHomeIdByExternalId(deviceId)).thenReturn(Optional.of(homeId));
        when(homeMemberRepository.findUserIdsByHomeId(homeId)).thenReturn(List.of(memberUserId));
        when(objectMapper.writeValueAsString(any(NotificationService.NotificationPayload.class)))
                .thenReturn("{\"homeId\":10,\"deviceId\":\"esp32-01\",\"event\":\"DOORBELL\"}");

        notificationService.dispatchEvent(deviceId, "DOORBELL");

        ArgumentCaptor<NotificationService.NotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(NotificationService.NotificationPayload.class);
        verify(objectMapper, times(1)).writeValueAsString(payloadCaptor.capture());

        NotificationService.NotificationPayload captured = payloadCaptor.getValue();
        assertEquals(homeId, captured.homeId());
        assertEquals(deviceId, captured.deviceId());
        assertEquals("DOORBELL", captured.event());
        assertNotNull(captured.timestamp());

        // Deve enviar para o membro da casa
        verify(memberEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        // NÃO deve enviar para o usuário que não pertence à casa
        verify(otherEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void dispatchEvent_DevePermitirNomeCustomizadoDeEvento() throws Exception {
        Long homeId = 10L;
        String deviceId = "esp32-02";
        Long memberUserId = 1L;

        SseEmitter emitter = mock(SseEmitter.class);
        notificationService.addEmitter(memberUserId, emitter);

        when(deviceRepository.findHomeIdByExternalId(deviceId)).thenReturn(Optional.of(homeId));
        when(homeMemberRepository.findUserIdsByHomeId(homeId)).thenReturn(List.of(memberUserId));
        when(objectMapper.writeValueAsString(any(NotificationService.NotificationPayload.class)))
                .thenReturn("{\"homeId\":10,\"deviceId\":\"esp32-02\",\"event\":\"COMMAND_SUCCESS\"}");

        notificationService.dispatchEvent("custom-event", deviceId, "COMMAND_SUCCESS");

        verify(objectMapper, times(1)).writeValueAsString(any(NotificationService.NotificationPayload.class));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void dispatchEvent_DeveRemoverEmitter_QuandoOcorrerErroDeEnvio() throws Exception {
        Long homeId = 10L;
        String deviceId = "esp32-01";
        Long memberUserId = 1L;

        SseEmitter failingEmitter = mock(SseEmitter.class);
        doThrow(new IOException("Broken pipe")).when(failingEmitter).send(any(SseEmitter.SseEventBuilder.class));

        notificationService.addEmitter(memberUserId, failingEmitter);
        assertEquals(1, notificationService.getActiveEmittersCount());

        when(deviceRepository.findHomeIdByExternalId(deviceId)).thenReturn(Optional.of(homeId));
        when(homeMemberRepository.findUserIdsByHomeId(homeId)).thenReturn(List.of(memberUserId));
        when(objectMapper.writeValueAsString(any(NotificationService.NotificationPayload.class)))
                .thenReturn("{\"homeId\":10,\"deviceId\":\"esp32-01\",\"event\":\"DOORBELL\"}");

        notificationService.dispatchEvent(deviceId, "DOORBELL");

        assertEquals(0, notificationService.getActiveEmittersCount());
        assertEquals(0, notificationService.getActiveEmittersCountForUser(memberUserId));
    }

    @Test
    void dispatchEvent_NaoDevePropagarExcecao_QuandoSerializacaoJsonFalhar() throws Exception {
        Long homeId = 10L;
        String deviceId = "esp32-01";
        Long memberUserId = 1L;

        SseEmitter emitter = mock(SseEmitter.class);
        notificationService.addEmitter(memberUserId, emitter);

        when(deviceRepository.findHomeIdByExternalId(deviceId)).thenReturn(Optional.of(homeId));
        when(homeMemberRepository.findUserIdsByHomeId(homeId)).thenReturn(List.of(memberUserId));
        when(objectMapper.writeValueAsString(any(NotificationService.NotificationPayload.class)))
                .thenThrow(new JsonProcessingException("Serialization failed") {});

        assertDoesNotThrow(() -> notificationService.dispatchEvent(deviceId, "DOORBELL"));

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        assertEquals(1, notificationService.getActiveEmittersCount());
    }

    @Test
    void dispatchEvent_NaoDeveDisparar_QuandoDispositivoNaoEncontrado() {
        when(deviceRepository.findHomeIdByExternalId("unknown-device")).thenReturn(Optional.empty());

        notificationService.dispatchEvent("unknown-device", "DOORBELL");

        verify(homeMemberRepository, never()).findUserIdsByHomeId(anyLong());
        verifyNoInteractions(objectMapper);
    }

    @Test
    void dispatchEvent_NaoDeveDisparar_QuandoCasaNaoPossuirMembros() {
        Long homeId = 99L;
        when(deviceRepository.findHomeIdByExternalId("esp32-empty")).thenReturn(Optional.of(homeId));
        when(homeMemberRepository.findUserIdsByHomeId(homeId)).thenReturn(List.of());

        notificationService.dispatchEvent("esp32-empty", "DOORBELL");

        verifyNoInteractions(objectMapper);
    }

    @Test
    void notificationPayload_ConstrutoresDevemInicializarCamposCorretamente() {
        NotificationService.NotificationPayload payloadCompleto =
                new NotificationService.NotificationPayload(5L, "esp32-03", "ALERT");

        assertEquals(5L, payloadCompleto.homeId());
        assertEquals("esp32-03", payloadCompleto.deviceId());
        assertEquals("ALERT", payloadCompleto.event());
        assertNotNull(payloadCompleto.timestamp());

        NotificationService.NotificationPayload payloadLegado =
                new NotificationService.NotificationPayload("esp32-04", "ALERT");

        assertNull(payloadLegado.homeId());
        assertEquals("esp32-04", payloadLegado.deviceId());
        assertEquals("ALERT", payloadLegado.event());
        assertNotNull(payloadLegado.timestamp());
    }
}
