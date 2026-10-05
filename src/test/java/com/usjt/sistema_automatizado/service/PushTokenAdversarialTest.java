package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.PushTokenRequest;
import com.usjt.sistema_automatizado.dto.response.PushTokenResponse;
import com.usjt.sistema_automatizado.mapper.PushTokenMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.PushToken;
import com.usjt.sistema_automatizado.model.enums.PushPlatform;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.PushTokenRepository;
import com.usjt.sistema_automatizado.service.impl.PushTokenServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Adversarial and empirical stress tests for {@link PushTokenServiceImpl}.
 *
 * Exercises edge cases, lifecycle anomalies, ownership reassignment,
 * cross-tenant revocation attempts, and validation boundaries.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PushToken Service - Adversarial & Lifecycle Verification")
class PushTokenAdversarialTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private AppUserRepository userRepository;

    @Spy
    private PushTokenMapper pushTokenMapper = new PushTokenMapper();

    @InjectMocks
    private PushTokenServiceImpl pushTokenService;

    private AppUser userAlice;
    private AppUser userBob;

    @BeforeEach
    void setUp() {
        userAlice = new AppUser();
        userAlice.setId(100L);
        userAlice.setEmail("alice@residence.local");

        userBob = new AppUser();
        userBob.setId(200L);
        userBob.setEmail("bob@residence.local");
    }

    @Nested
    @DisplayName("Lifecycle: Duplicate Registration & Ownership Reassignment")
    class DuplicateRegistrationAndOwnership {

        @Test
        @DisplayName("Same user re-registers same token with different platform: updates platform and touches lastUsedAt")
        void sameUserDuplicateToken_UpdatesPlatformAndLastUsedAt() {
            String tokenValue = "fcm_token_device_alpha_123";
            PushToken existingToken = new PushToken(userAlice, tokenValue, PushPlatform.ANDROID);
            existingToken.setId(1L);
            LocalDateTime initialLastUsed = LocalDateTime.now().minusDays(1);
            existingToken.setLastUsedAt(initialLastUsed);

            when(userRepository.findById(100L)).thenReturn(Optional.of(userAlice));
            when(pushTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(existingToken));
            when(pushTokenRepository.save(existingToken)).thenReturn(existingToken);

            PushTokenRequest request = new PushTokenRequest(tokenValue, PushPlatform.IOS);
            PushTokenResponse response = pushTokenService.registerToken(100L, request);

            assertNotNull(response);
            assertEquals(100L, response.userId());
            assertEquals(PushPlatform.IOS, response.platform());
            assertEquals(tokenValue, response.token());

            // Verify entity mutation
            assertEquals(PushPlatform.IOS, existingToken.getPlatform());
            assertNotNull(existingToken.getLastUsedAt());
            assertTrue(existingToken.getLastUsedAt().isAfter(initialLastUsed));
            assertEquals(userAlice, existingToken.getUser());
            verify(pushTokenRepository, times(1)).save(existingToken);
        }

        @Test
        @DisplayName("Different user registers existing token (device reassignment): transfers ownership to new user")
        void differentUserDuplicateToken_TransfersOwnershipCleanly() {
            String sharedToken = "fcm_token_reassigned_device_999";

            // Originally belonged to Alice
            PushToken existingToken = new PushToken(userAlice, sharedToken, PushPlatform.ANDROID);
            existingToken.setId(42L);

            // Now Bob logs in on that device and registers the token
            when(userRepository.findById(200L)).thenReturn(Optional.of(userBob));
            when(pushTokenRepository.findByToken(sharedToken)).thenReturn(Optional.of(existingToken));
            when(pushTokenRepository.save(existingToken)).thenReturn(existingToken);

            PushTokenRequest request = new PushTokenRequest(sharedToken, PushPlatform.WEB);
            PushTokenResponse response = pushTokenService.registerToken(200L, request);

            assertNotNull(response);
            assertEquals(200L, response.userId());
            assertEquals(PushPlatform.WEB, response.platform());
            assertEquals(sharedToken, response.token());

            // Verify ownership transferred in entity
            assertEquals(userBob, existingToken.getUser());
            assertEquals(PushPlatform.WEB, existingToken.getPlatform());
            verify(pushTokenRepository, times(1)).save(existingToken);
        }

        @Test
        @DisplayName("Rapid successive platform transitions on same token survive cleanly")
        void successivePlatformTransitions() {
            String tokenValue = "fcm_token_chameleon";
            PushToken token = new PushToken(userAlice, tokenValue, PushPlatform.WEB);
            token.setId(77L);

            when(userRepository.findById(100L)).thenReturn(Optional.of(userAlice));
            when(pushTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(token));
            when(pushTokenRepository.save(any(PushToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

            for (PushPlatform platform : List.of(PushPlatform.ANDROID, PushPlatform.IOS, PushPlatform.WEB, PushPlatform.ANDROID)) {
                PushTokenRequest req = new PushTokenRequest(tokenValue, platform);
                PushTokenResponse res = pushTokenService.registerToken(100L, req);
                assertEquals(platform, res.platform());
                assertEquals(platform, token.getPlatform());
            }

            verify(pushTokenRepository, times(4)).save(token);
        }
    }

    @Nested
    @DisplayName("Revocation Idempotence & Cross-User Security")
    class RevocationIdempotenceAndSecurity {

        @Test
        @DisplayName("Revoking existing token belonging to user deletes token from repository")
        void revokeOwnToken_DeletesSuccessfully() {
            String tokenValue = "token_alice_mobile";
            PushToken token = new PushToken(userAlice, tokenValue, PushPlatform.ANDROID);

            when(pushTokenRepository.findByTokenAndUserId(tokenValue, 100L)).thenReturn(Optional.of(token));

            pushTokenService.revokeToken(100L, tokenValue);

            verify(pushTokenRepository, times(1)).findByTokenAndUserId(tokenValue, 100L);
            verify(pushTokenRepository, times(1)).delete(token);
        }

        @Test
        @DisplayName("Revoking non-existent token is idempotent and does not throw or delete")
        void revokeNonExistentToken_IsIdempotentNoOp() {
            String ghostToken = "token_does_not_exist_404";
            when(pushTokenRepository.findByTokenAndUserId(ghostToken, 100L)).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> pushTokenService.revokeToken(100L, ghostToken));

            verify(pushTokenRepository, times(1)).findByTokenAndUserId(ghostToken, 100L);
            verify(pushTokenRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Adversarial attack: User Alice attempts to revoke User Bob's token -> safe no-op, Bob's token preserved")
        void crossUserRevocationAttempt_DoesNotDeleteVictimToken() {
            String bobsToken = "bobs_private_fcm_token";

            // When Alice (userId=100) queries by token and her own userId, repository returns empty
            when(pushTokenRepository.findByTokenAndUserId(bobsToken, 100L)).thenReturn(Optional.empty());

            // Alice invokes revoke on Bob's token
            assertDoesNotThrow(() -> pushTokenService.revokeToken(100L, bobsToken));

            // Verify repository delete was never called!
            verify(pushTokenRepository, never()).delete(any());
            verify(pushTokenRepository, never()).deleteByToken(any());
            verify(pushTokenRepository, never()).deleteByTokenAndUserId(any(), any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Revoking with null or whitespace token is safe no-op")
        void revokeBlankOrNullToken_IsSafeNoOp(String invalidToken) {
            assertDoesNotThrow(() -> pushTokenService.revokeToken(100L, invalidToken));
            verifyNoInteractions(pushTokenRepository);
        }

        @Test
        @DisplayName("Revoking with null userId is safe no-op")
        void revokeWithNullUserId_IsSafeNoOp() {
            assertDoesNotThrow(() -> pushTokenService.revokeToken(null, "valid_token"));
            verifyNoInteractions(pushTokenRepository);
        }
    }

    @Nested
    @DisplayName("Validation Constraints & Edge Cases")
    class ValidationConstraints {

        @Test
        @DisplayName("Null userId throws IllegalArgumentException")
        void registerWithNullUserId_ThrowsIllegalArgumentException() {
            PushTokenRequest request = new PushTokenRequest("token123", PushPlatform.ANDROID);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> pushTokenService.registerToken(null, request));
            assertTrue(ex.getMessage().contains("Identificador de usuário é obrigatório"));
            verifyNoInteractions(pushTokenRepository, userRepository);
        }

        @Test
        @DisplayName("Null request throws IllegalArgumentException")
        void registerWithNullRequest_ThrowsIllegalArgumentException() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> pushTokenService.registerToken(100L, null));
            assertTrue(ex.getMessage().contains("Token de push é obrigatório"));
            verifyNoInteractions(pushTokenRepository, userRepository);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Blank token string throws IllegalArgumentException")
        void registerWithBlankToken_ThrowsIllegalArgumentException(String blankToken) {
            PushTokenRequest request = new PushTokenRequest(blankToken, PushPlatform.ANDROID);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> pushTokenService.registerToken(100L, request));
            assertTrue(ex.getMessage().contains("Token de push é obrigatório"));
            verifyNoInteractions(pushTokenRepository, userRepository);
        }

        @Test
        @DisplayName("Null platform throws IllegalArgumentException")
        void registerWithNullPlatform_ThrowsIllegalArgumentException() {
            PushTokenRequest request = new PushTokenRequest("token123", null);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> pushTokenService.registerToken(100L, request));
            assertTrue(ex.getMessage().contains("Plataforma é obrigatória"));
            verifyNoInteractions(pushTokenRepository, userRepository);
        }

        @Test
        @DisplayName("Non-existent user throws EntityNotFoundException")
        void registerWithNonExistentUser_ThrowsEntityNotFoundException() {
            PushTokenRequest request = new PushTokenRequest("token123", PushPlatform.ANDROID);
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class,
                    () -> pushTokenService.registerToken(999L, request));
            verify(pushTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("Listing tokens for null userId returns empty list safely")
        void getTokensByNullUser_ReturnsEmptyList() {
            List<PushTokenResponse> tokens = pushTokenService.getTokensByUser(null);
            assertNotNull(tokens);
            assertTrue(tokens.isEmpty());
            verifyNoInteractions(pushTokenRepository);
        }
    }
}
