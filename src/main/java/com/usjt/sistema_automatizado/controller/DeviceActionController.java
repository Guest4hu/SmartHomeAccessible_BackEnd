package com.usjt.sistema_automatizado.controller;

import com.usjt.sistema_automatizado.dto.request.CommandRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.CommandResponse;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.service.DeviceActionService;
import com.usjt.sistema_automatizado.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices") // Rota livre de homeId, ideal para o hardware
@RequiredArgsConstructor
public class DeviceActionController {

    private final DeviceActionService deviceActionService;

    @PostMapping("/active")
    public ResponseEntity<DeviceResponse> receiveHeartbeat(@RequestBody @Valid TelemetryRequest request) {
        DeviceResponse response = deviceActionService.processHeartbeat(request);
        return ResponseEntity.ok(response);
    }
    @PostMapping("/{deviceId}/commands")
    public ResponseEntity<CommandResponse> sendCommand(
            @PathVariable Long deviceId,
            @Valid @RequestBody CommandRequest request,
            @AuthenticationPrincipal Long requesterId
    ) {
        CommandResponse response = deviceActionService.sendCommand(deviceId, request, requesterId);
        return ResponseEntity.ok(response);
    }
}