package com.usjt.sistema_automatizado.dto.response;

import com.usjt.sistema_automatizado.model.enums.HomeRole;
import java.time.LocalDateTime;

public record HomeMemberResponse(
        Long id,
        Long userId,
        String name,
        String email,
        HomeRole role,
        LocalDateTime joinedAt
) {}