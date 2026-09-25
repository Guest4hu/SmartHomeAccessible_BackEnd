package com.usjt.sistema_automatizado.dto.response;

import com.usjt.sistema_automatizado.model.enums.CommandType;
import java.time.LocalDateTime;

public record CommandResponse(
        String deviceExternalId,
        CommandType type,
        String status, // Ex: "DELIVERED", "QUEUED" ou "FAILED"
        LocalDateTime dispatchedAt,
        String requestedBy
) {}