package com.usjt.sistema_automatizado.dto.response;

public record LoginResponse(
        String token,
        String type
) {}