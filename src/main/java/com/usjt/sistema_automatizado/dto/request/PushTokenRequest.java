package com.usjt.sistema_automatizado.dto.request;

import com.usjt.sistema_automatizado.model.enums.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PushTokenRequest(
        @NotBlank(message = "Token não pode ser vazio")
        @Size(max = 512, message = "Token deve ter no máximo 512 caracteres")
        String token,

        @NotNull(message = "Plataforma é obrigatória")
        PushPlatform platform
) {}
