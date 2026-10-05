package com.usjt.sistema_automatizado.model.entity;

import com.usjt.sistema_automatizado.model.enums.PushPlatform;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PushTokenTest {

    @Test
    void prePersist_DevePreencherCreatedAtEmUtc() {
        AppUser user = new AppUser();
        user.setId(1L);

        PushToken token = new PushToken(user, "fcm-token-test", PushPlatform.ANDROID);
        assertNull(token.getCreatedAt());

        token.prePersist();

        assertNotNull(token.getCreatedAt());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        assertTrue(token.getCreatedAt().isBefore(now.plusSeconds(2)));
        assertTrue(token.getCreatedAt().isAfter(now.minusSeconds(2)));
    }

    @Test
    void prePersist_NaoDeveSobrescreverCreatedAtSeJaExistir() {
        PushToken token = new PushToken();
        LocalDateTime past = LocalDateTime.of(2025, 1, 1, 12, 0);
        token.setCreatedAt(past);

        token.prePersist();

        assertEquals(past, token.getCreatedAt());
    }

    @Test
    void updateUsage_DeveAtualizarLastUsedAtEmUtc() {
        PushToken token = new PushToken();
        assertNull(token.getLastUsedAt());

        token.updateUsage();

        assertNotNull(token.getLastUsedAt());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        assertTrue(token.getLastUsedAt().isBefore(now.plusSeconds(2)));
        assertTrue(token.getLastUsedAt().isAfter(now.minusSeconds(2)));
    }
}
