package com.usjt.sistema_automatizado.controller;

import com.usjt.sistema_automatizado.dto.request.EventRequest;
import com.usjt.sistema_automatizado.dto.response.EventResponse;
import com.usjt.sistema_automatizado.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    // ESP32 envia para aqui (temporário até usarmos MQTT)
    @PostMapping("/events")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request));
    }

    // Frontend consulta os eventos da casa
    @GetMapping("/homes/{homeId}/events")
    public ResponseEntity<List<EventResponse>> getHomeEvents(
            @PathVariable Long homeId,
            @RequestParam(defaultValue = "false") boolean pendingOnly,
            @AuthenticationPrincipal Long requesterId
    ) {
        return ResponseEntity.ok(eventService.getHomeEvents(homeId, requesterId, pendingOnly));
    }

    // Utilizador clica no botão "Já vi" (Acknowledge)
    @PatchMapping("/events/{id}/acknowledge")
    public ResponseEntity<EventResponse> acknowledgeEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal Long requesterId
    ) {
        return ResponseEntity.ok(eventService.acknowledgeEvent(id, requesterId));
    }
}