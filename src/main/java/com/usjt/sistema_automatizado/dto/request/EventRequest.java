package com.usjt.sistema_automatizado.dto.request;

import com.usjt.sistema_automatizado.model.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EventRequest(
        @NotNull(message = "A versão do payload é obrigatória")
        Integer v,

        @NotBlank(message = "O ID do dispositivo é obrigatório")
        String deviceId, // O external_id que vem do ESP32 (ex: "esp32-sala-01")

        @NotNull(message = "O timestamp do evento é obrigatório")
        LocalDateTime ts,

        @NotNull(message = "O tipo de evento é obrigatório")
        EventType type
) {}