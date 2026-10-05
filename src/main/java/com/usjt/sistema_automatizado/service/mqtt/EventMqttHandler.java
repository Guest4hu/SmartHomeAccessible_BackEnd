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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Processador especializado em mensagens recebidas no canal de eventos ({@code devices/{deviceId}/event}).
 *
 * <p>Responsabilidades centrais:
 * <ul>
 *   <li>Identificar confirmações de comandos ({@code COMMAND_SUCCESS}/{@code COMMAND_FAILED}), extrair o
 *       {@code correlationId} e resolver o {@link java.util.concurrent.CompletableFuture} síncrono correspondente
 *       no {@link CommandAckService}.</li>
 *   <li>Persistir eventos físicos espontâneos (campainha, presença) no histórico do banco de dados.</li>
 *   <li>Disparar notificações em tempo real via SSE (Server-Sent Events) restritas aos membros da casa do dispositivo.</li>
 *   <li>Despachar push notifications móveis via {@link PushNotificationService} em acionamentos de campainha (DOORBELL).</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventMqttHandler implements MqttMessageHandler {

    private final EventService eventService;
    private final NotificationService notificationService;
    private final MqttMessageMapper mqttMapper;
    private final CommandAckService commandAckService;
    private final DeviceRepository deviceRepository;
    private final PushNotificationService pushNotificationService;

    @Override
    public boolean supports(MqttMessageType messageType) {
        return messageType == MqttMessageType.EVENT;
    }

    @Override
    public void handle(MqttEnvelope envelope) throws Exception {
        EventRequest request = mqttMapper.toEventRequest(envelope);

        // Verifica se é uma confirmação de comando (ACK do firmware).
        // Usa o correlationId para resolver o Future exato — eventos físicos
        // espontâneos (DOORBELL, PRESENCE_DETECTED) não carregam correlationId
        // e portanto nunca resolvem nenhum Future acidentalmente.
        if (request.type() == EventType.COMMAND_SUCCESS || request.type() == EventType.COMMAND_FAILED) {
            String correlationId = mqttMapper.extrairCorrelationId(envelope);
            CommandDeliveryStatus ackStatus = (request.type() == EventType.COMMAND_SUCCESS)
                    ? CommandDeliveryStatus.DELIVERED
                    : CommandDeliveryStatus.FAILED;
            commandAckService.resolverAck(envelope.deviceId(), correlationId, ackStatus);
        }

        // Persiste o evento normalmente (histórico no banco)
        try {
            eventService.createEvent(request);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.warn("[Roteador] Dispositivo {} não cadastrado no banco; evento não persistido: {}", envelope.deviceId(), e.getMessage());
        }

        // Dispara SSE para o painel web (item 2 da Seção 5 do CONTEXTO.md)
        notificationService.dispatchEvent(envelope.deviceId(), request.type().name());
        log.info("[Roteador] Evento disparado -> Device: {}, Tipo: {}", envelope.deviceId(), request.type());

        // Dispara Push Notification móvel para eventos de campainha (DOORBELL)
        if (request.type() == EventType.DOORBELL) {
            Optional<Long> homeIdOpt = deviceRepository.findHomeIdByExternalId(envelope.deviceId());
            if (homeIdOpt.isPresent()) {
                Long homeId = homeIdOpt.get();
                String title = "Campainha Acionada";
                String body = "A campainha foi acionada pelo dispositivo " + envelope.deviceId() + ".";
                Map<String, String> data = Map.of(
                        "eventType", "DOORBELL",
                        "deviceId", envelope.deviceId(),
                        "urgency", "WARNING"
                );
                pushNotificationService.sendNotificationToHome(homeId, title, body, data);
            } else {
                log.warn("[EventMqttHandler] Dispositivo {} não vinculado a residência; push notification de campainha ignorada.", envelope.deviceId());
            }
        }
    }
}
