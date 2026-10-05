package com.usjt.sistema_automatizado.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HomeRequest(
        @NotBlank(message = "O nome da casa é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        String name
) {}