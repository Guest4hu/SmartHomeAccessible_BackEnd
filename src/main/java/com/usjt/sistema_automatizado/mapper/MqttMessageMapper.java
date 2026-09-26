package com.usjt.sistema_automatizado.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.dto.request.EventRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.model.entity.Event;
import com.usjt.sistema_automatizado.model.enums.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class MqttMessageMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Transforma o Envelope de Rede no DTO de Telemetria
    public TelemetryRequest toTelemetryRequest(MqttEnvelope envelope) throws Exception {
        JsonNode node = objectMapper.readTree(envelope.jsonPayload());

        return new TelemetryRequest(
                node.path("v").asInt(1),
                envelope.deviceId(),
                LocalDateTime.now(ZoneOffset.UTC),
                node.has("temperature") ? node.get("temperature").asDouble() : null,
                node.has("humidity") ? node.get("humidity").asDouble() : null,
                node.has("luminosity") ? node.get("luminosity").asDouble() : null
        );
    }

    // Transforma o Envelope de Evento no DTO de Eventos
    public EventRequest toEventRequest(MqttEnvelope envelope) throws Exception {
        JsonNode node = objectMapper.readTree(envelope.jsonPayload());

        // 1. Extrai o tipo de evento (seguro com o seu Enum)
        String typeString = node.path("type").asText("");
        EventType type = EventType.fromString(typeString);

        // 2. Constrói o EventRequest respeitando a ordem e os tipos exatos do seu Record
        return new EventRequest(
                node.path("v").asInt(1),           // v: Lê do JSON ou assume 1 como padrão
                envelope.deviceId(),               // deviceId: Já vem limpo do tópico (Envelope)
                LocalDateTime.now(ZoneOffset.UTC), // ts: Carimbo de tempo exato do servidor
                type                               // type: O Enum validado
        );
    }

    // Extrai apenas o tipo de evento (Campainha, Presença, etc.)
    public String toEventType(MqttEnvelope envelope) throws Exception {
        JsonNode node = objectMapper.readTree(envelope.jsonPayload());
        return node.path("type").asText("UNKNOWN_EVENT");
    }
}