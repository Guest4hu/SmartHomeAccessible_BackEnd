package com.usjt.sistema_automatizado.dto.response;

import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import java.time.LocalDateTime;

public record DeviceResponse(
        Long id,
        String externalId,
        String name,
        String room,
        DeviceStatus status,
        LocalDateTime lastSeenAt,
        String firmwareVersion
) {
}