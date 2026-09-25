package com.usjt.sistema_automatizado.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AutomationConfigRequest(
        @NotNull(message = "O limite superior de temperatura é obrigatório")
        Double fanOnAbove,

        @NotNull(message = "O limite inferior de temperatura é obrigatório")
        Double fanOffBelow,

        @NotNull(message = "O limite de luminosidade é obrigatório")
        Double darkBelow,

        @Size(max = 50, message = "O padrão da campainha não pode exceder 50 caracteres")
        String doorbellPattern
) {}