package com.usjt.sistema_automatizado.service.mqtt;

import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;
import com.usjt.sistema_automatizado.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Processador especializado em mensagens recebidas no canal de status ({@code devices/{deviceId}/status}).
 *
 * <p>Responsável por atualizar o estado de conectividade dos dispositivos a partir de publicações
 * de ciclo de vida e mensagens Last Will and Testament (LWT) retidas pelo broker MQTT.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusMqttHandler implements MqttMessageHandler {

    private final DeviceService deviceService;

    @Override
    public boolean supports(MqttMessageType messageType) {
        return messageType == MqttMessageType.STATUS;
    }

    @Override
    public void handle(MqttEnvelope envelope) throws Exception {
        String rawStatus = envelope.jsonPayload() != null ? envelope.jsonPayload().trim().toUpperCase() : "";

        try {
            DeviceStatus status = DeviceStatus.valueOf(rawStatus);
            deviceService.updateDeviceStatus(envelope.deviceId(), status);
            log.info("[Roteador] Status do dispositivo {} atualizado para: {}", envelope.deviceId(), status);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.debug("[Roteador] Status ignorado para dispositivo não cadastrado: {}", envelope.deviceId());
        } catch (IllegalArgumentException e) {
            log.warn("[Roteador] Status desconhecido/inválido recebido para o dispositivo {}: '{}'", envelope.deviceId(), rawStatus);
        }
    }
}
