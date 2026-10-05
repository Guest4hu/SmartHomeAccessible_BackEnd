package com.usjt.sistema_automatizado.dto.response;

import com.usjt.sistema_automatizado.model.enums.PushPlatform;

import java.time.LocalDateTime;

public record PushTokenResponse(
        Long id,
        Long userId,
        String token,
        PushPlatform platform,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt
) {}
