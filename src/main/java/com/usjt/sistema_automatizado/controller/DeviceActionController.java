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

/**
 * Controller responsável pelas ações diretas sobre dispositivos IoT (heartbeat e disparo de comandos).
 */
@RestController
@RequestMapping("/api/v1/devices") // Rota livre de homeId, ideal para o hardware
@RequiredArgsConstructor
public class DeviceActionController {

    private final DeviceActionService deviceActionService;

    /**
     * Recebe sinal de atividade periódico (heartbeat) comutando o status do dispositivo para ONLINE.
     *
     * @param request payload com dados básicos e identificador do dispositivo
     * @return resposta HTTP com o estado atualizado do dispositivo
     */
    @PostMapping("/active")
    public ResponseEntity<DeviceResponse> receiveHeartbeat(@RequestBody @Valid TelemetryRequest request) {
        DeviceResponse response = deviceActionService.processHeartbeat(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Envia um comando operacional para o dispositivo e aguarda a confirmação de entrega do firmware.
     *
     * @param deviceId identificador do dispositivo
     * @param request comando a ser executado
     * @param requesterId identificador do morador autenticado
     * @return resposta com o status de entrega (DELIVERED, FAILED ou TIMEOUT)
     */
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