package com.usjt.sistema_automatizado.dto.response;

import com.usjt.sistema_automatizado.model.enums.CommandDeliveryStatus;
import com.usjt.sistema_automatizado.model.enums.CommandType;
import java.time.LocalDateTime;

public record CommandResponse(
        String deviceExternalId,
        CommandType type,
        CommandDeliveryStatus status,
        LocalDateTime dispatchedAt,
        String requestedBy
) {}