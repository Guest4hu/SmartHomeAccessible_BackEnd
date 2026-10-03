package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;
import com.usjt.sistema_automatizado.service.mqtt.MqttMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Roteador central de mensagens MQTT de entrada.
 *
 * <p>Implementa o padrão Strategy, delegando a responsabilidade de processamento para implementações
 * especializadas de {@link MqttMessageHandler} com base na categoria extraída do tópico.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttRouterService {

    private final List<MqttMessageHandler> handlers;

    /**
     * Analisa o tópico e payload do envelope MQTT, aplicando guardas defensivas e delegando ao handler adequado.
     *
     * <p>Permite payloads textuais simples (como {@code ONLINE}/{@code OFFLINE}) para mensagens de status LWT,
     * enquanto exige formatação JSON estrita para telemetria e eventos.</p>
     *
     * @param envelope mensagem empacotada com tópico, deviceId e payload
     */
    public void routeMessage(MqttEnvelope envelope) {
        if (envelope == null || envelope.jsonPayload() == null) {
            log.warn("[Roteador] Envelope ou payload nulo recebido");
            return;
        }

        try {
            // 1. Descobrimos o tipo de mensagem baseando-nos no tópico
            MqttMessageType messageType = MqttMessageType.fromTopic(envelope.topico());
            log.debug("[Roteador] Mensagem recebida -> tópico={} tipo={}", envelope.topico(), messageType);

            // 2. Guarda de payload: exige JSON apenas para TELEMETRY e EVENT; permite payloads textuais (ex: ONLINE/OFFLINE do LWT) para STATUS
            if ((messageType == MqttMessageType.TELEMETRY || messageType == MqttMessageType.EVENT)
                    && !envelope.jsonPayload().trim().startsWith("{")) {
                log.debug("[Roteador] Payload não-JSON ignorado no tópico {}: {}", envelope.topico(), envelope.jsonPayload());
                return;
            }

            if (messageType == MqttMessageType.COMMAND) {
                log.warn("[Roteador] Backend não deveria receber comandos: {}", envelope.topico());
                return;
            }


            // 3. Resolução dinâmica da estratégia
            MqttMessageHandler handler = handlers.stream()
                    .filter(h -> h.supports(messageType))
                    .findFirst()
                    .orElse(null);

            if (handler != null) {
                handler.handle(envelope);
            } else {
                log.warn("[Roteador] Tópico desconhecido ignorado: {}", envelope.topico());
            }

        } catch (Exception e) {
            // Loga stack trace completo para facilitar diagnóstico em produção
            log.error("[Roteador] Falha ao rotear payload do tópico {}: {} | payload={}",
                    envelope.topico(), e.getMessage(), envelope.jsonPayload(), e);
        }
    }
}