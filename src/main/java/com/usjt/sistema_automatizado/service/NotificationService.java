package com.usjt.sistema_automatizado.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Serviço de transmissão de notificações em tempo real via Server-Sent Events (SSE).
 *
 * <p><b>Isolamento Multi-Tenant:</b> Garante que alertas visuais de campainha e eventos físicos
 * sejam encaminhados exclusivamente aos moradores cadastrados na residência à qual o dispositivo pertence,
 * impedindo o vazamento de notificações entre famílias distintas.</p>
 *
 * <p><b>Modelo de Concorrência:</b> Utiliza {@link ConcurrentHashMap} associado a {@link CopyOnWriteArrayList}
 * para suportar com segurança múltiplas conexões/abas simultâneas por usuário com descarte automático de emissores inativos.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;

    // Mapa Thread-Safe indexado por ID de usuário: permite múltiplas abas/aparelhos por morador
    private final Map<Long, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public record NotificationPayload(
            Long homeId,
            String deviceId,
            String event,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
            LocalDateTime timestamp
    ) {
        public NotificationPayload(Long homeId, String deviceId, String event) {
            this(homeId, deviceId, event, LocalDateTime.now(ZoneOffset.UTC));
        }

        public NotificationPayload(String deviceId, String event) {
            this(null, deviceId, event, LocalDateTime.now(ZoneOffset.UTC));
        }
    }

    /**
     * Registra e inicializa uma conexão persistente SSE vinculada ao usuário autenticado.
     *
     * @param userId identificador do usuário conectado
     * @return emissor SSE configurado com callbacks de ciclo de vida e timeout de 60 minutos
     * @throws IllegalArgumentException se o identificador de usuário for nulo
     */
    public SseEmitter subscribe(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Identificador de usuário é obrigatório para conexão SSE.");
        }

        // Conexão com timeout de 60 minutos
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> removeEmitter(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    // Remove conexão desconectada e limpa chave do mapa se não houver mais emissores ativos
    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId, emitters);
            }
        }
    }

    /**
     * Dispara um evento utilizando o canal de evento SSE padrão ("bell-ring").
     *
     * @param deviceId identificador de hardware (externalId) do dispositivo emissor
     * @param eventType nome do evento (ex: DOORBELL, PRESENCE_DETECTED)
     */
    public void dispatchEvent(String deviceId, String eventType) {
        dispatchEvent("bell-ring", deviceId, eventType);
    }

    /**
     * Resolve a residência do dispositivo e dispara o evento SSE para todos os membros dessa residência.
     *
     * @param sseEventName nome do evento SSE recebido pelo cliente EventSource
     * @param deviceId identificador de hardware do dispositivo
     * @param eventType tipo de evento a ser propagado
     */
    public void dispatchEvent(String sseEventName, String deviceId, String eventType) {
        Optional<Long> homeIdOpt = deviceRepository.findHomeIdByExternalId(deviceId);
        if (homeIdOpt.isEmpty()) {
            log.warn("[Notification] Dispositivo {} não encontrado; notificação SSE ignorada.", deviceId);
            return;
        }

        Long homeId = homeIdOpt.get();
        NotificationPayload payload = new NotificationPayload(homeId, deviceId, eventType, LocalDateTime.now(ZoneOffset.UTC));
        dispatchToHome(homeId, sseEventName, payload);
    }

    /**
     * Envia o payload do evento em formato JSON exclusivamente para os emissores SSE dos moradores vinculados à residência.
     *
     * @param homeId identificador da residência
     * @param sseEventName nome do evento SSE
     * @param payload objeto contendo metadados e instante do evento
     */
    public void dispatchToHome(Long homeId, String sseEventName, NotificationPayload payload) {
        List<Long> memberUserIds = homeMemberRepository.findUserIdsByHomeId(homeId);
        if (memberUserIds.isEmpty()) {
            log.debug("[Notification] Residência {} não possui moradores cadastrados.", homeId);
            return;
        }

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("[Notification] Erro ao serializar payload SSE para homeId={}: {}", homeId, e.getMessage(), e);
            return;
        }

        for (Long userId : memberUserIds) {
            List<SseEmitter> emitters = userEmitters.get(userId);
            if (emitters != null && !emitters.isEmpty()) {
                for (SseEmitter emitter : emitters) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name(sseEventName)
                                .data(jsonPayload));
                    } catch (IOException | IllegalStateException e) {
                        removeEmitter(userId, emitter);
                    }
                }
            }
        }
    }

    // Acessores package-private para testes unitários
    void addEmitter(Long userId, SseEmitter emitter) {
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    int getActiveEmittersCount() {
        return userEmitters.values().stream().mapToInt(List::size).sum();
    }

    int getActiveEmittersCountForUser(Long userId) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        return (emitters != null) ? emitters.size() : 0;
    }
}