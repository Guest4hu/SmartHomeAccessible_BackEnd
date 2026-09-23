package com.usjt.sistema_automatizado.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceRequest {

    @NotNull(message = "O ID da casa é obrigatório")
    private Long homeId;

    @NotBlank(message = "O identificador externo é obrigatório")
    @Size(max = 100, message = "O tamanho máximo é de 100 caracteres")
    private String externalId;

    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 100, message = "O tamanho máximo é de 100 caracteres")
    private String name;

    @Size(max = 100, message = "O tamanho máximo é de 100 caracteres")
    private String room;
}