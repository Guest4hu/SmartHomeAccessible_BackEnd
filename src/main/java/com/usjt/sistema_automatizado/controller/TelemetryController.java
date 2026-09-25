package com.usjt.sistema_automatizado.controller;

import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.dto.response.MetricInfoResponse;
import com.usjt.sistema_automatizado.dto.response.MetricSeriesResponse;
import com.usjt.sistema_automatizado.model.enums.MetricType;
import com.usjt.sistema_automatizado.service.DeviceService;
import com.usjt.sistema_automatizado.service.TelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @PostMapping("/telemetry")
    public ResponseEntity<Void> saveTelemetry(@Valid @RequestBody TelemetryRequest request) {
        telemetryService.saveTelemetry(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/devices/{deviceId}/metrics")
    public ResponseEntity<List<MetricInfoResponse>> getAvailableMetrics(@PathVariable Long deviceId) {
        List<MetricInfoResponse> responses = telemetryService.getAvailableMetrics(deviceId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/devices/{deviceId}/metrics/{metric}")
    public ResponseEntity<MetricSeriesResponse> getMetricSeries(
            @PathVariable Long deviceId,
            @PathVariable MetricType metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String interval
    ) {
        MetricSeriesResponse response = telemetryService.getMetricSeries(deviceId, metric, from, to, interval);
        return ResponseEntity.ok(response);
    }
}