package com.usjt.sistema_automatizado.mapper;

import com.usjt.sistema_automatizado.dto.request.CommandRequest;
import org.springframework.stereotype.Component;

@Component
public class DeviceActionMapper {

    /**
     * Converte o CommandRequest em JSON para envio ao firmware via MQTT.
     *
     * O campo "correlationId" é um UUID gerado pelo backend para esta requisição.
     * O firmware o ecoa intacto na confirmação (COMMAND_SUCCESS/FAILED), permitindo
     * ao backend correlacionar o ACK com a requisição HTTP correta sem interferência
     * de eventos físicos espontâneos (DOORBELL, PRESENCE_DETECTED, etc.).
     *
     * Payload gerado:
     * { "action": "FAN_ON", "correlationId": "a1b2c3d4-e5f6-..." }
     */
    public String toCommandJson(CommandRequest request, String correlationId) {
        // Traduz o enum do Spring para o comando esperado pelo firmware C++
        String actionValue = switch (request.type()) {
            case TURN_ON  -> "FAN_ON";
            case TURN_OFF -> "FAN_OFF";
            case BLINK_LED -> "BLINK_LED";
            default       -> request.type().name();
        };

        return String.format(
                "{ \"action\": \"%s\", \"correlationId\": \"%s\" }",
                actionValue,
                correlationId
        );
    }
}
