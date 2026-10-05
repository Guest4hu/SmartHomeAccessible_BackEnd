package com.usjt.sistema_automatizado.mapper;

import com.usjt.sistema_automatizado.dto.request.PushTokenRequest;
import com.usjt.sistema_automatizado.dto.response.PushTokenResponse;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.PushToken;
import org.springframework.stereotype.Component;

/**
 * Componente de mapeamento bidirecional entre a entidade {@link PushToken} e seus respectivos DTOs.
 */
@Component
public class PushTokenMapper {

    public PushToken toEntity(PushTokenRequest request, AppUser user) {
        if (request == null) {
            return null;
        }
        return new PushToken(user, request.token(), request.platform());
    }

    public PushTokenResponse toResponse(PushToken entity) {
        if (entity == null) {
            return null;
        }
        return new PushTokenResponse(
                entity.getId(),
                entity.getUser() != null ? entity.getUser().getId() : null,
                entity.getToken(),
                entity.getPlatform(),
                entity.getCreatedAt(),
                entity.getLastUsedAt()
        );
    }
}
