package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.dto.request.EventRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.mapper.EventMapper;
import com.usjt.sistema_automatizado.mapper.MqttMessageMapper;
import com.usjt.sistema_automatizado.model.enums.EventType;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttRouterService {

    private final EventService eventService;
    private final TelemetryService telemetryService;
    private final NotificationService notificationService;
    private final MqttMessageMapper mqttMapper;

    public void routeMessage(MqttEnvelope envelope) {
        if (!envelope.jsonPayload().trim().startsWith("{")) return;

        try {
            // 1. Descobrimos o tipo de mensagem baseando-nos no tópico
            MqttMessageType messageType = MqttMessageType.fromTopic(envelope.topico());

            // 2. Roteamos com um Switch elegante
            switch (messageType) {
                case TELEMETRY -> processTelemetry(envelope);
                case EVENT -> processEvent(envelope);
                case STATUS -> log.info("[Roteador] Status recebido (a implementar): {}", envelope.jsonPayload());
                case COMMAND -> log.warn("[Roteador] Backend não deveria receber comandos: {}", envelope.topico());
                case UNKNOWN -> log.warn("[Roteador] Tópico desconhecido ignorado: {}", envelope.topico());
            }

        } catch (Exception e) {
            log.error("[Roteador] Falha ao rotear payload: {}", e.getMessage());
        }
    }

    private void processTelemetry(MqttEnvelope envelope) throws Exception {
        TelemetryRequest request = mqttMapper.toTelemetryRequest(envelope);
        telemetryService.saveTelemetry(request);
        log.info("[Roteador] Telemetria entregue -> Device: {}", envelope.deviceId());
    }

    private void processEvent(MqttEnvelope envelope) throws Exception {
        EventRequest request = mqttMapper.toEventRequest(envelope);
        eventService.createEvent(request);
        log.info("[Roteador] Evento disparado -> Device: {}, Tipo: {}", envelope.deviceId(), request.type());
    }
}