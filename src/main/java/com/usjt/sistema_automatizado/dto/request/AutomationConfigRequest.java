package com.usjt.sistema_automatizado.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
        String doorbellPattern,

        @NotNull(message = "O componente R da cor da campainha é obrigatório")
        @Min(value = 0, message = "O valor R deve ser entre 0 e 255")
        @Max(value = 255, message = "O valor R deve ser entre 0 e 255")
        Integer bellR,

        @NotNull(message = "O componente G da cor da campainha é obrigatório")
        @Min(value = 0, message = "O valor G deve ser entre 0 e 255")
        @Max(value = 255, message = "O valor G deve ser entre 0 e 255")
        Integer bellG,

        @NotNull(message = "O componente B da cor da campainha é obrigatório")
        @Min(value = 0, message = "O valor B deve ser entre 0 e 255")
        @Max(value = 255, message = "O valor B deve ser entre 0 e 255")
        Integer bellB
) {
    public AutomationConfigRequest(Double fanOnAbove, Double fanOffBelow, Double darkBelow, String doorbellPattern) {
        this(fanOnAbove, fanOffBelow, darkBelow, doorbellPattern, 255, 0, 0);
    }
}