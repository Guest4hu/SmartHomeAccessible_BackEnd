package com.usjt.sistema_automatizado.mapper;

import com.usjt.sistema_automatizado.dto.request.PushTokenRequest;
import com.usjt.sistema_automatizado.dto.response.PushTokenResponse;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.PushToken;
import com.usjt.sistema_automatizado.model.enums.PushPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PushTokenMapperTest {

    private PushTokenMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PushTokenMapper();
    }

    @Test
    void toEntity_DeveMapearCamposCorretamente() {
        AppUser user = new AppUser();
        user.setId(5L);

        PushTokenRequest request = new PushTokenRequest("token-abc", PushPlatform.IOS);

        PushToken entity = mapper.toEntity(request, user);

        assertNotNull(entity);
        assertEquals("token-abc", entity.getToken());
        assertEquals(PushPlatform.IOS, entity.getPlatform());
        assertEquals(user, entity.getUser());
    }

    @Test
    void toEntity_DeveRetornarNull_QuandoRequestForNulo() {
        assertNull(mapper.toEntity(null, new AppUser()));
    }

    @Test
    void toResponse_DeveMapearCamposCorretamente() {
        AppUser user = new AppUser();
        user.setId(5L);

        PushToken entity = new PushToken(user, "token-xyz", PushPlatform.WEB);
        entity.setId(12L);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setLastUsedAt(now);

        PushTokenResponse response = mapper.toResponse(entity);

        assertNotNull(response);
        assertEquals(12L, response.id());
        assertEquals(5L, response.userId());
        assertEquals("token-xyz", response.token());
        assertEquals(PushPlatform.WEB, response.platform());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.lastUsedAt());
    }

    @Test
    void toResponse_DeveRetornarNull_QuandoEntityForNulo() {
        assertNull(mapper.toResponse(null));
    }
}
