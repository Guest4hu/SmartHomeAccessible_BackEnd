package com.usjt.sistema_automatizado.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ObjectMapper objectMapper;

    // Lista Thread-Safe para guardar todos os utilizadores/navegadores conectados
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public record NotificationPayload(
            String deviceId,
            String event,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
            LocalDateTime timestamp
    ) {
        public NotificationPayload(String deviceId, String event) {
            this(deviceId, event, LocalDateTime.now(ZoneOffset.UTC));
        }
    }

    // Método que o Controller usa para inscrever um novo navegador
    public SseEmitter subscribe() {
        // Cria uma conexão com timeout de 60 minutos
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);
        emitters.add(emitter);

        // Limpeza automática quando o utilizador fechar a aba
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        return emitter;
    }

    // Método compatível mantido com nome padrão ("bell-ring")
    public void dispatchEvent(String deviceId, String eventType) {
        dispatchEvent("bell-ring", deviceId, eventType);
    }

    // Sobrecarga para permitir nomes customizados caso o frontend evolua
    public void dispatchEvent(String sseEventName, String deviceId, String eventType) {
        NotificationPayload payload = new NotificationPayload(deviceId, eventType, LocalDateTime.now(ZoneOffset.UTC));
        dispatchEvent(sseEventName, payload);
    }

    // Sobrecarga aceitando diretamente o payload estruturado
    public void dispatchEvent(String sseEventName, NotificationPayload payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            for (SseEmitter emitter : emitters) {
                try {
                    // Envia o JSON para o navegador com o nome de evento especificado
                    emitter.send(SseEmitter.event()
                            .name(sseEventName)
                            .data(jsonPayload));
                } catch (IOException | IllegalStateException e) {
                    emitters.remove(emitter); // Se falhar (ex: conexão perdida), removemos da lista
                }
            }
        } catch (JsonProcessingException e) {
            log.error("[Notification] Erro ao serializar payload SSE para deviceId={}: {}",
                    payload.deviceId(), e.getMessage(), e);
        }
    }

    // Acessores package-private para testes unitários
    void addEmitter(SseEmitter emitter) {
        emitters.add(emitter);
    }

    int getActiveEmittersCount() {
        return emitters.size();
    }
}