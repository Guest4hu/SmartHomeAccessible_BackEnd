package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.config.mqtt.MqttGateway; // Ajuste para o pacote correto do seu gateway
import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

/**
 * Serviço de adaptação de transporte MQTT, responsável pelo envio e recebimento de mensagens via Spring Integration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttService {

    private final MqttGateway mqttGateway;
    private final MqttRouterService routerService;

    /**
     * Publica um comando JSON no tópico MQTT padronizado do dispositivo ({@code devices/{externalId}/cmd}).
     *
     * @param externalId identificador de hardware do ESP32
     * @param jsonPayload payload formatado contendo a ação e o correlationId
     */
    public void sendCommand(String externalId, String jsonPayload) {
        // Monta o tópico no formato correto: devices/{externalId}/cmd com QoS 1 e retained false
        String topic = "devices/" + externalId + "/" + MqttMessageType.COMMAND.getSuffix();
        log.info("[MQTT] A enviar comando para {}: {}", topic, jsonPayload);
        mqttGateway.sendToMqtt(topic, 1, false, jsonPayload);
    }

    /**
     * Intercepta mensagens recebidas no canal de entrada MQTT, extrai metadados do tópico e encaminha ao roteador.
     *
     * @param message mensagem Spring contendo o payload e os cabeçalhos MQTT
     */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleIncomingMessage(Message<String> message) {
        String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC).toString();
        String payload = message.getPayload();

        String[] parts = topic.split("/");
        if (parts.length < 3) return; // Proteção contra tópicos inválidos

        // Empacota a mensagem e envia para a camada de negócio
        MqttEnvelope envelope = new MqttEnvelope(topic, parts[1], payload);
        routerService.routeMessage(envelope);
    }
}