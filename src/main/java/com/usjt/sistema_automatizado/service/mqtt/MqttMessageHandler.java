package com.usjt.sistema_automatizado.service.mqtt;

import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;

public interface MqttMessageHandler {

    boolean supports(MqttMessageType messageType);

    void handle(MqttEnvelope envelope) throws Exception;
}
