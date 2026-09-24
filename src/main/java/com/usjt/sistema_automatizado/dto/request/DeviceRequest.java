package com.usjt.sistema_automatizado.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceRequest(
        @NotNull(message = "O ID da casa é obrigatório")
        Long homeId,

        @NotBlank(message = "O identificador externo é obrigatório")
        @Size(max = 100, message = "O tamanho máximo é de 100 caracteres")
        String externalId,

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100, message = "O tamanho máximo é de 100 caracteres")
        String name,

        @Size(max = 100, message = "O tamanho máximo é de 100 caracteres")
        String room
) {}