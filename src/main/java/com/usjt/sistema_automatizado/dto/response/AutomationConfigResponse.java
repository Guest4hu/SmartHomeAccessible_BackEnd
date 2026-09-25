package com.usjt.sistema_automatizado.dto.response;

import java.time.LocalDateTime;

public record AutomationConfigResponse(
        Long id,
        Long deviceId,
        Double fanOnAbove,
        Double fanOffBelow,
        Double darkBelow,
        String doorbellPattern,
        LocalDateTime updatedAt,
        String updatedByName
) {}