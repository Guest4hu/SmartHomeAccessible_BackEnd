package com.usjt.sistema_automatizado.dto.response;

import com.usjt.sistema_automatizado.model.enums.EventType;
import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String deviceExternalId,
        String deviceName,
        String room,
        EventType type,
        LocalDateTime occurredAt,
        LocalDateTime receivedAt,
        LocalDateTime acknowledgedAt,
        String acknowledgedByName
) {}