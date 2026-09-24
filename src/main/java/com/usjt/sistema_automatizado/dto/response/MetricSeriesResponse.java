package com.usjt.sistema_automatizado.dto.response;

import com.usjt.sistema_automatizado.model.enums.MetricType;
import java.time.LocalDateTime;
import java.util.List;

public record MetricSeriesResponse(
        Long deviceId,
        MetricType metric,
        String unit,
        LocalDateTime from,
        LocalDateTime to,
        String interval,
        List<Point> points
) {
    public record Point(
            LocalDateTime t,
            Double avg,
            Double min,
            Double max
    ) {}
}