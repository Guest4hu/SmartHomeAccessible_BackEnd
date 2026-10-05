package com.usjt.sistema_automatizado.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usjt.sistema_automatizado.model.enums.SensoryChannel;
import com.usjt.sistema_automatizado.model.enums.UrgencyLevel;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
            UrgencyLevel urgency,
            SensoryChannel sensoryChannel,
            String altText,
            String ttsText,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
            LocalDateTime timestamp
    ) {
        public NotificationPayload(Long homeId, String deviceId, String event, LocalDateTime timestamp) {
            this(
                    homeId,
                    deviceId,
                    event,
                    inferUrgency(event),
                    inferSensoryChannel(event),
                    inferAltText(deviceId, event),
                    inferTtsText(event),
                    timestamp != null ? timestamp : LocalDateTime.now(ZoneOffset.UTC)
            );
        }

        public NotificationPayload(Long homeId, String deviceId, String event) {
            this(homeId, deviceId, event, LocalDateTime.now(ZoneOffset.UTC));
        }

        public NotificationPayload(String deviceId, String event) {
            this(null, deviceId, event, LocalDateTime.now(ZoneOffset.UTC));
        }

        public static UrgencyLevel inferUrgency(String event) {
            if (event == null) return UrgencyLevel.INFO;
            return switch (event.toUpperCase()) {
                case "DOORBELL", "COMMAND_FAILED", "FILE_ERROR", "CRITICAL_ALERT" -> UrgencyLevel.WARNING;
                case "EMERGENCY", "GAS_LEAK", "FIRE" -> UrgencyLevel.CRITICAL;
                default -> UrgencyLevel.INFO;
            };
        }

        public static SensoryChannel inferSensoryChannel(String event) {
            if (event == null) return SensoryChannel.VISUAL;
            return switch (event.toUpperCase()) {
                case "DOORBELL", "COMMAND_FAILED" -> SensoryChannel.MULTIMODAL;
                default -> SensoryChannel.VISUAL;
            };
        }

        public static String inferAltText(String deviceId, String event) {
            String dev = (deviceId != null) ? deviceId : "dispositivo";
            if (event == null) return "Notificação do " + dev;
            return switch (event.toUpperCase()) {
                case "DOORBELL" -> "A campainha foi acionada pelo dispositivo " + dev + ".";
                case "PRESENCE_DETECTED" -> "Presença detectada no ambiente pelo dispositivo " + dev + ".";
                case "COMMAND_SUCCESS" -> "Comando executado com sucesso no dispositivo " + dev + ".";
                case "COMMAND_FAILED" -> "Falha na execução de comando no dispositivo " + dev + ".";
                default -> "Evento " + event + " registrado no dispositivo " + dev + ".";
            };
        }

        public static String inferTtsText(String event) {
            if (event == null) return "Nova notificação.";
            return switch (event.toUpperCase()) {
                case "DOORBELL" -> "A campainha está tocando.";
                case "PRESENCE_DETECTED" -> "Presença detectada.";
                case "COMMAND_SUCCESS" -> "Comando concluído com sucesso.";
                case "COMMAND_FAILED" -> "Atenção: falha ao executar comando.";
                default -> "Novo evento recebido.";
            };
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

    /**
     * Dispara evento com metadados de acessibilidade explicitamente customizados.
     *
     * @param sseEventName nome do evento SSE
     * @param deviceId identificador do dispositivo
     * @param eventType tipo de evento
     * @param urgency nível de urgência
     * @param channel canal sensorial
     * @param altText texto alternativo para leitores de tela
     * @param ttsText texto para síntese de voz (TTS)
     */
    public void dispatchEvent(String sseEventName, String deviceId, String eventType,
                              UrgencyLevel urgency, SensoryChannel channel,
                              String altText, String ttsText) {
        Optional<Long> homeIdOpt = deviceRepository.findHomeIdByExternalId(deviceId);
        if (homeIdOpt.isEmpty()) {
            log.warn("[Notification] Dispositivo {} não encontrado; notificação SSE ignorada.", deviceId);
            return;
        }

        Long homeId = homeIdOpt.get();
        NotificationPayload payload = new NotificationPayload(
                homeId,
                deviceId,
                eventType,
                urgency != null ? urgency : NotificationPayload.inferUrgency(eventType),
                channel != null ? channel : NotificationPayload.inferSensoryChannel(eventType),
                altText != null ? altText : NotificationPayload.inferAltText(deviceId, eventType),
                ttsText != null ? ttsText : NotificationPayload.inferTtsText(eventType),
                LocalDateTime.now(ZoneOffset.UTC)
        );
        dispatchToHome(homeId, sseEventName, payload);
    }

    /**
     * Envia heartbeat periódico a cada 25 segundos para manter conexões SSE ativas
     * contra encerramentos prematuros por proxies reversos e descartar conexões inativas.
     */
    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        if (userEmitters.isEmpty()) {
            return;
        }
        log.trace("[Notification] Enviando heartbeat SSE para emissores ativos.");
        userEmitters.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .comment("keep-alive")
                            .data("{\"status\":\"PING\"}"));
                } catch (IOException | IllegalStateException e) {
                    log.debug("[Notification] Conexão SSE inativa para userId={}, removendo...", userId);
                    removeEmitter(userId, emitter);
                }
            }
        });
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