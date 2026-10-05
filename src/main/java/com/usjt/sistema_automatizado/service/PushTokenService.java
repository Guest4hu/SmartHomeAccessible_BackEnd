package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.PushTokenRequest;
import com.usjt.sistema_automatizado.dto.response.PushTokenResponse;

import java.util.List;

/**
 * Contrato de serviço para gerenciamento do ciclo de vida de tokens push FCM de moradores.
 */
public interface PushTokenService {

    /**
     * Registra ou atualiza um push token associado ao usuário autenticado de forma idempotente.
     *
     * @param userId identificador do usuário autenticado (requesterId)
     * @param request payload contendo token e plataforma
     * @return DTO com os dados do token persistido
     */
    PushTokenResponse registerToken(Long userId, PushTokenRequest request);

    /**
     * Revoga um push token pertencente ao usuário autenticado de forma idempotente e segura.
     *
     * @param userId identificador do usuário autenticado (requesterId)
     * @param token string do token a ser revogado
     */
    void revokeToken(Long userId, String token);

    /**
     * Lista todos os push tokens registrados para o usuário informado.
     *
     * @param userId identificador do usuário autenticado
     * @return lista de DTOs dos tokens
     */
    List<PushTokenResponse> getTokensByUser(Long userId);
}
