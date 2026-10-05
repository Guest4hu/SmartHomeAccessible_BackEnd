package com.usjt.sistema_automatizado.dto.response;

import java.time.LocalDateTime;

public record AutomationConfigResponse(
        Long id,
        Long deviceId,
        Double fanOnAbove,
        Double fanOffBelow,
        Double darkBelow,
        String doorbellPattern,
        Integer bellR,
        Integer bellG,
        Integer bellB,
        LocalDateTime updatedAt,
        String updatedByName
) {}