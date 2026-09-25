package com.usjt.sistema_automatizado.dto.request;

import com.usjt.sistema_automatizado.model.enums.CommandType;
import jakarta.validation.constraints.NotNull;

public record CommandRequest(
        @NotNull(message = "O tipo de comando é obrigatório")
        CommandType type,

        // Campo opcional: Usado caso o comando exija um valor numérico (ex: intensidade do LED)
        Double value
) {}