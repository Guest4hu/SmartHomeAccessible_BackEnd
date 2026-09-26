package com.usjt.sistema_automatizado.dto;

import lombok.Builder;

@Builder
public record MqttEnvelope(
        String topico,
        String deviceId,
        String jsonPayload
) {}