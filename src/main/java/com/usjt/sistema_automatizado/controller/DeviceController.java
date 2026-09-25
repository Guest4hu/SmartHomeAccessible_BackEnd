package com.usjt.sistema_automatizado.controller;

import com.usjt.sistema_automatizado.dto.request.DeviceRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/homes/{homeId}/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    public ResponseEntity<DeviceResponse> createDevice(
            @PathVariable Long homeId,
            @Valid @RequestBody DeviceRequest request,
            @AuthenticationPrincipal Long requesterId
    ) {
        DeviceResponse response = deviceService.createDevice(homeId, request, requesterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping
    public ResponseEntity<List<DeviceResponse>> listDevices(
            @PathVariable Long homeId,
            @AuthenticationPrincipal Long requesterId
    ) {
        List<DeviceResponse> responses = deviceService.listDevices(homeId, requesterId);
        return ResponseEntity.ok(responses);
    }
}