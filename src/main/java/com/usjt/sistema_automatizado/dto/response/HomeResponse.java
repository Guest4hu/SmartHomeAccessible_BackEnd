package com.usjt.sistema_automatizado.dto.response;

import java.time.LocalDateTime;

public record HomeResponse(
        Long id,
        String name,
        LocalDateTime createdAt
) {}