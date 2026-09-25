package com.usjt.sistema_automatizado.dto.request;

public record RegisterRequest(
        String name,
        String email,
        String password
) {}