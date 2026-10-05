package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.config.FirebaseProperties;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.PushToken;
import com.usjt.sistema_automatizado.model.enums.PushPlatform;
import com.usjt.sistema_automatizado.repository.PushTokenRepository;
import com.usjt.sistema_automatizado.service.impl.PushNotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PushNotificationService - Adversarial Multi-Tenant & Resilience Verification")
class PushNotificationAdversarialTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    private FirebaseProperties firebaseProperties;
    private PushNotificationServiceImpl pushNotificationService;

    @BeforeEach
    void setUp() {
        firebaseProperties = new FirebaseProperties();
        firebaseProperties.setEnabled(false); // Dry-run fallback mode
        firebaseProperties.setDryRun(true);

        pushNotificationService = new PushNotificationServiceImpl(pushTokenRepository, firebaseProperties);
        pushNotificationService.init();
    }

    @Test
    @DisplayName("Zero tokens in home: executes gracefully with 0 errors and does not touch repository save")
    void sendNotificationToHome_EmptyHome_ExecutesGracefully() {
        when(pushTokenRepository.findAllByHomeId(999L)).thenReturn(List.of());

        assertDoesNotThrow(() -> pushNotificationService.sendNotificationToHome(
                999L, "Doorbell", "Visitor at gate", Map.of("eventType", "DOORBELL")));

        verify(pushTokenRepository, times(1)).findAllByHomeId(999L);
        verify(pushTokenRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Null homeId: executes gracefully without throwing exception")
    void sendNotificationToHome_NullHomeId_DoesNotThrow() {
        assertDoesNotThrow(() -> pushNotificationService.sendNotificationToHome(
                null, "Doorbell", "Visitor", null));

        verifyNoInteractions(pushTokenRepository);
    }

    @Test
    @DisplayName("Multi-tenant isolation: tokens belonging to Home 1 are strictly segregated from Home 2")
    void sendNotificationToHome_StrictlySegregatesByHome() {
        AppUser residentHome1 = new AppUser();
        residentHome1.setId(10L);

        List<PushToken> home1Tokens = List.of(
                new PushToken(residentHome1, "tok_home1_a", PushPlatform.ANDROID),
                new PushToken(residentHome1, "tok_home1_b", PushPlatform.IOS)
        );

        when(pushTokenRepository.findAllByHomeId(1L)).thenReturn(home1Tokens);

        pushNotificationService.sendNotificationToHome(1L, "Alerta", "Incêndio", Map.of());

        // Verify only Home 1 was queried
        verify(pushTokenRepository, times(1)).findAllByHomeId(1L);
        verify(pushTokenRepository, never()).findAllByHomeId(2L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(pushTokenRepository, times(1)).saveAll(captor.capture());

        List<PushToken> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(t -> t.getToken().startsWith("tok_home1")));
    }

    @Test
    @DisplayName("Stress: High token volume (100 tokens in residence) touches all timestamps in batch")
    void sendNotificationToHome_HighVolume_TouchesAllTimestamps() {
        AppUser user = new AppUser();
        user.setId(50L);

        List<PushToken> bulkTokens = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            bulkTokens.add(new PushToken(user, "tok_bulk_" + i, PushPlatform.ANDROID));
        }

        when(pushTokenRepository.findAllByHomeId(77L)).thenReturn(bulkTokens);

        pushNotificationService.sendNotificationToHome(77L, "Mass Alert", "Evacuate", Map.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(pushTokenRepository, times(1)).saveAll(captor.capture());

        List<PushToken> saved = captor.getValue();
        assertEquals(100, saved.size());
        for (PushToken pt : saved) {
            assertNotNull(pt.getLastUsedAt(), "Every token should have its lastUsedAt refreshed");
        }
    }
}
