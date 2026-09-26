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
    private final CommandAckService commandAckService;

    public void routeMessage(MqttEnvelope envelope) {
        // Descarta mensagens que não são JSON (ex: payload "ONLINE"/"OFFLINE" do status LWT)
        if (!envelope.jsonPayload().trim().startsWith("{")) {
            log.debug("[Roteador] Payload não-JSON ignorado no tópico {}: {}", envelope.topico(), envelope.jsonPayload());
            return;
        }

        try {
            // 1. Descobrimos o tipo de mensagem baseando-nos no tópico
            MqttMessageType messageType = MqttMessageType.fromTopic(envelope.topico());
            log.debug("[Roteador] Mensagem recebida -> tópico={} tipo={}", envelope.topico(), messageType);

            // 2. Roteamos com um Switch elegante
            switch (messageType) {
                case TELEMETRY -> processTelemetry(envelope);
                case EVENT     -> processEvent(envelope);
                case STATUS    -> log.info("[Roteador] Status recebido (a implementar): {}", envelope.jsonPayload());
                case COMMAND   -> log.warn("[Roteador] Backend não deveria receber comandos: {}", envelope.topico());
                case UNKNOWN   -> log.warn("[Roteador] Tópico desconhecido ignorado: {}", envelope.topico());
            }

        } catch (Exception e) {
            // Loga stack trace completo para facilitar diagnóstico em produção
            log.error("[Roteador] Falha ao rotear payload do tópico {}: {} | payload={}",
                    envelope.topico(), e.getMessage(), envelope.jsonPayload(), e);
        }
    }

    private void processTelemetry(MqttEnvelope envelope) throws Exception {
        TelemetryRequest request = mqttMapper.toTelemetryRequest(envelope);
        telemetryService.saveTelemetry(request);
        log.info("[Roteador] Telemetria entregue -> Device: {}", envelope.deviceId());
    }

    private void processEvent(MqttEnvelope envelope) throws Exception {
        EventRequest request = mqttMapper.toEventRequest(envelope);

        // Verifica se é uma confirmação de comando (ACK do firmware).
        // Usa o correlationId para resolver o Future exato — eventos físicos
        // espontâneos (DOORBELL, PRESENCE_DETECTED) não carregam correlationId
        // e portanto nunca resolvem nenhum Future acidentalmente.
        if (request.type() == EventType.COMMAND_SUCCESS || request.type() == EventType.COMMAND_FAILED) {
            String correlationId = mqttMapper.extrairCorrelationId(envelope);
            String ackStatus = (request.type() == EventType.COMMAND_SUCCESS) ? "DELIVERED" : "FAILED";
            commandAckService.resolverAck(correlationId, ackStatus);
        }

        // Persiste o evento normalmente (histórico no banco)
        eventService.createEvent(request);

        // Dispara SSE para o painel web (item 2 da Seção 5 do CONTEXTO.md)
        notificationService.dispatchEvent(envelope.deviceId(), request.type().name());
        log.info("[Roteador] Evento disparado -> Device: {}, Tipo: {}", envelope.deviceId(), request.type());
    }
}