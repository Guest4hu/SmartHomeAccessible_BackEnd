package com.usjt.sistema_automatizado.service.impl;

import com.usjt.sistema_automatizado.dto.request.PushTokenRequest;
import com.usjt.sistema_automatizado.dto.response.PushTokenResponse;
import com.usjt.sistema_automatizado.mapper.PushTokenMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.PushToken;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.PushTokenRepository;
import com.usjt.sistema_automatizado.service.PushTokenService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushTokenServiceImpl implements PushTokenService {

    private final PushTokenRepository pushTokenRepository;
    private final AppUserRepository appUserRepository;
    private final PushTokenMapper pushTokenMapper;

    @Override
    @Transactional
    public PushTokenResponse registerToken(Long userId, PushTokenRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("Identificador de usuário é obrigatório.");
        }
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("Token de push é obrigatório.");
        }
        if (request.platform() == null) {
            throw new IllegalArgumentException("Plataforma é obrigatória.");
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + userId));

        PushToken pushToken = pushTokenRepository.findByToken(request.token())
                .map(existing -> {
                    log.debug("[PushToken] Atualizando token existente para userId={}", userId);
                    existing.setUser(user);
                    existing.setPlatform(request.platform());
                    existing.updateUsage();
                    return existing;
                })
                .orElseGet(() -> {
                    log.debug("[PushToken] Cadastrando novo token para userId={}", userId);
                    PushToken newToken = new PushToken(user, request.token(), request.platform());
                    newToken.updateUsage();
                    return newToken;
                });

        PushToken saved = pushTokenRepository.save(pushToken);
        return pushTokenMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void revokeToken(Long userId, String token) {
        if (userId == null || token == null || token.isBlank()) {
            return;
        }

        pushTokenRepository.findByTokenAndUserId(token, userId)
                .ifPresentOrElse(pushToken -> {
                    pushTokenRepository.delete(pushToken);
                    log.debug("[PushToken] Token revogado com sucesso para userId={}", userId);
                }, () -> {
                    log.debug("[PushToken] Token não encontrado para userId={} ou já revogado; nenhuma ação necessária", userId);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<PushTokenResponse> getTokensByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return pushTokenRepository.findByUserId(userId).stream()
                .map(pushTokenMapper::toResponse)
                .toList();
    }
}
