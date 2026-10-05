package com.usjt.sistema_automatizado.service.mqtt;

import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.mapper.MqttMessageMapper;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;
import com.usjt.sistema_automatizado.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Processador especializado em mensagens recebidas no canal de telemetria ({@code devices/{deviceId}/telemetry}).
 *
 * <p>Converte os dados recebidos dos sensores de ambiente e delega a gravação no formato longo ao {@link TelemetryService}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryMqttHandler implements MqttMessageHandler {

    private final TelemetryService telemetryService;
    private final MqttMessageMapper mqttMapper;

    @Override
    public boolean supports(MqttMessageType messageType) {
        return messageType == MqttMessageType.TELEMETRY;
    }

    @Override
    public void handle(MqttEnvelope envelope) throws Exception {
        TelemetryRequest request = mqttMapper.toTelemetryRequest(envelope);
        try {
            telemetryService.saveTelemetry(request);
            log.info("[Roteador] Telemetria entregue -> Device: {}", envelope.deviceId());
        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.warn("[Roteador] Dispositivo {} não cadastrado no banco; telemetria descartada: {}", envelope.deviceId(), e.getMessage());
        }
    }
}
