package com.usjt.sistema_automatizado.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record TelemetryRequest(
        @NotNull(message = "A versão do payload é obrigatória")
        Integer v,

        @NotBlank(message = "O ID do dispositivo é obrigatório")
        String deviceId,

        @NotNull(message = "O timestamp da leitura é obrigatório")
        LocalDateTime ts,

        Double temperature,
        Double humidity,
        Double luminosity
) {
    public TelemetryRequest(String deviceId, Double temperature, Double humidity, Double luminosity) {
        this(1, deviceId, LocalDateTime.now(ZoneOffset.UTC), temperature, humidity, luminosity);
    }

    public TelemetryRequest(String deviceId, Double temperature, Double humidity, Integer luminosity) {
        this(1, deviceId, LocalDateTime.now(ZoneOffset.UTC), temperature, humidity, luminosity != null ? luminosity.doubleValue() : null);
    }
}