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

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttService {

    private final MqttGateway mqttGateway;
    private final MqttRouterService routerService;

    // ==========================================
    // OUTBOUND: ENVIAR COMANDOS PARA O ESP32
    // ==========================================
    public void sendCommand(String externalId, String jsonPayload) {
        // Monta o tópico no formato correto: devices/{externalId}/cmd
        String topic = "devices/" + externalId + "/cmd";
        log.info("[MQTT] A enviar comando para {}: {}", topic, jsonPayload);
        mqttGateway.sendToMqtt(topic, jsonPayload);
    }

    // ==========================================
    // INBOUND: RECEBER DADOS DO ESP32
    // ==========================================
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