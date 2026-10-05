package com.usjt.sistema_automatizado.dto.response;

import com.usjt.sistema_automatizado.model.enums.MetricType;

public record MetricInfoResponse(
        MetricType metric,
        String label,
        String unit
) {}