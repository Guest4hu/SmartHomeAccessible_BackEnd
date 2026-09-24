package com.usjt.sistema_automatizado.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

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
) {}