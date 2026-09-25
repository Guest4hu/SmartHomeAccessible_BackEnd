package com.usjt.sistema_automatizado.controller;

import com.usjt.sistema_automatizado.dto.request.AutomationConfigRequest;
import com.usjt.sistema_automatizado.dto.response.AutomationConfigResponse;
import com.usjt.sistema_automatizado.service.AutomationConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/config")
@RequiredArgsConstructor
public class AutomationConfigController {

    private final AutomationConfigService automationConfigService;

    @GetMapping
    public ResponseEntity<AutomationConfigResponse> getConfig(
            @PathVariable Long deviceId,
            @RequestHeader("X-User-Id") Long requesterId
    ) {
        return ResponseEntity.ok(automationConfigService.getConfig(deviceId, requesterId));
    }

    @PutMapping
    public ResponseEntity<AutomationConfigResponse> updateConfig(
            @PathVariable Long deviceId,
            @Valid @RequestBody AutomationConfigRequest request,
            @RequestHeader("X-User-Id") Long requesterId
    ) {
        return ResponseEntity.ok(automationConfigService.updateConfig(deviceId, request, requesterId));
    }
}