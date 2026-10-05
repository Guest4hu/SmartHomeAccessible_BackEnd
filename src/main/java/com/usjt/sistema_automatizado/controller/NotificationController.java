package com.usjt.sistema_automatizado.controller;

import com.usjt.sistema_automatizado.dto.request.PushTokenRequest;
import com.usjt.sistema_automatizado.dto.response.PushTokenResponse;
import com.usjt.sistema_automatizado.service.NotificationService;
import com.usjt.sistema_automatizado.service.PushTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller responsável pelo streaming de eventos via SSE e gestão de push tokens móveis.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final PushTokenService pushTokenService;

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

    /**
     * Cadastra ou atualiza o push token do morador autenticado de forma idempotente.
     *
     * @param request dados contendo token e plataforma
     * @param requesterId identificador do usuário autenticado
     * @return dados do token registrado com HTTP 201 Created
     */
    @PostMapping("/push-tokens")
    public ResponseEntity<PushTokenResponse> registerPushToken(
            @Valid @RequestBody PushTokenRequest request,
            @AuthenticationPrincipal Long requesterId
    ) {
        PushTokenResponse response = pushTokenService.registerToken(requesterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Revoga o push token do morador autenticado de forma segura e idempotente.
     *
     * @param token string do token a revogar
     * @param requesterId identificador do usuário autenticado
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/push-tokens/{token}")
    public ResponseEntity<Void> revokePushToken(
            @PathVariable String token,
            @AuthenticationPrincipal Long requesterId
    ) {
        pushTokenService.revokeToken(requesterId, token);
        return ResponseEntity.noContent().build();
    }
}