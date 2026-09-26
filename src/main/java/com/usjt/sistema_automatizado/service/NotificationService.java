package com.usjt.sistema_automatizado.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {

    // Lista Thread-Safe para guardar todos os utilizadores/navegadores conectados
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

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

    // Método que o MQTT Listener usa para "gritar" para toda a gente
    public void dispatchEvent(String deviceId, String eventType) {
        String jsonPayload = String.format("{\"deviceId\": \"%s\", \"event\": \"%s\"}", deviceId, eventType);

        for (SseEmitter emitter : emitters) {
            try {
                // Envia o JSON para o navegador com o nome do evento "bell-ring"
                emitter.send(SseEmitter.event()
                        .name("bell-ring")
                        .data(jsonPayload));
            } catch (IOException e) {
                emitters.remove(emitter); // Se falhar (ex: net caiu), removemos da lista
            }
        }
    }
}