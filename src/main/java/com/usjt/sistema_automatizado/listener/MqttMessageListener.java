package com.usjt.sistema_automatizado.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.service.TelemetryService;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class MqttMessageListener {

    // 1. Instanciamos diretamente para evitar o erro de "Bean not found"
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 2. Injetamos a SUA regra de negócio
    private final TelemetryService telemetryService;

    public MqttMessageListener(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void processarMensagemRecebida(Message<String> message) {
        String topico = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC).toString();
        String jsonPayload = message.getPayload();

        try {
            if (topico.endsWith("/telemetry")) {
                // 1. Defesa contra "lixo" de outras pessoas no broker público
                if (!jsonPayload.trim().startsWith("{")) {
                    return; // Ignora silenciosamente tudo o que não for um JSON válido (como o "heartbeat")
                }

                // 2. Extrai o ID diretamente do Tópico (devices/MEU-ID/telemetry)
                String[] partesTopico = topico.split("/");
                if (partesTopico.length < 3) return; // Segurança extra
                String deviceIdExtraido = partesTopico[1];

                JsonNode node = objectMapper.readTree(jsonPayload);

                TelemetryRequest request = new TelemetryRequest(
                        node.has("v") ? node.get("v").asInt() : 1,
                        deviceIdExtraido, // <-- Agora usamos o ID do tópico (100% seguro)
                        LocalDateTime.now(ZoneOffset.UTC),
                        node.has("temperature") ? node.get("temperature").asDouble() : null,
                        node.has("humidity") ? node.get("humidity").asDouble() : null,
                        node.has("luminosity") ? node.get("luminosity").asDouble() : null
                );

                telemetryService.saveTelemetry(request);
                System.out.println("[MQTT] Sucesso! Telemetria do dispositivo [" + deviceIdExtraido + "] guardada.");

            } else if (topico.endsWith("/event")) {
                System.out.println("-> Evento de Campainha: " + jsonPayload);
            }
        } catch (Exception e) {
            // Pode haver outros JSONs estranhos na rede pública. O bloco catch impede a aplicação de cair.
            // Apenas ignoramos o erro silenciosamente em vez de sujar o terminal.
        }
    }
}