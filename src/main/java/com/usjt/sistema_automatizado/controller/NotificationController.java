package com.usjt.sistema_automatizado.controller;

import com.usjt.sistema_automatizado.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller responsável pelo streaming contínuo de eventos em tempo real via Server-Sent Events (SSE).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Estabelece uma conexão HTTP persistente (text/event-stream) para recebimento de alertas e eventos em tempo real.
     *
     * <p>A autenticação pode ser realizada tanto via cabeçalho {@code Authorization: Bearer} quanto via query param
     * {@code ?token=}, viabilizando a conexão nativa de clientes {@code EventSource} no frontend.</p>
     *
     * @param userId identificador do usuário autenticado
     * @return emissor SSE configurado para o usuário
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@AuthenticationPrincipal Long userId) {
        return notificationService.subscribe(userId);
    }
}