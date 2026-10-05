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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private AppUserRepository userRepository;

    @Spy
    private PushTokenMapper pushTokenMapper = new PushTokenMapper();

    @InjectMocks
    private PushTokenServiceImpl pushTokenService;

    private AppUser appUser;

    @BeforeEach
    void setUp() {
        appUser = new AppUser();
        appUser.setId(1L);
        appUser.setEmail("morador@sistema.local");
    }

    @Test
    void registerToken_DeveCadastrarNovoToken_QuandoTokenNaoExistir() {
        PushTokenRequest request = new PushTokenRequest("fcm-token-123", PushPlatform.ANDROID);

        when(userRepository.findById(1L)).thenReturn(Optional.of(appUser));
        when(pushTokenRepository.findByToken("fcm-token-123")).thenReturn(Optional.empty());

        PushToken savedToken = new PushToken(appUser, "fcm-token-123", PushPlatform.ANDROID);
        savedToken.setId(10L);
        when(pushTokenRepository.save(any(PushToken.class))).thenReturn(savedToken);

        PushTokenResponse response = pushTokenService.registerToken(1L, request);

        assertNotNull(response);
        assertEquals("fcm-token-123", response.token());
        assertEquals(PushPlatform.ANDROID, response.platform());
        assertEquals(1L, response.userId());

        ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
        verify(pushTokenRepository, times(1)).save(captor.capture());
        assertEquals("fcm-token-123", captor.getValue().getToken());
        assertEquals(appUser, captor.getValue().getUser());
    }

    @Test
    void registerToken_DeveAtualizarPlataformaELastUsedAt_QuandoTokenJaExistirParaOMesmoUsuario() {
        PushTokenRequest request = new PushTokenRequest("fcm-token-existente", PushPlatform.IOS);

        PushToken existingToken = new PushToken(appUser, "fcm-token-existente", PushPlatform.ANDROID);
        existingToken.setId(20L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(appUser));
        when(pushTokenRepository.findByToken("fcm-token-existente")).thenReturn(Optional.of(existingToken));
        when(pushTokenRepository.save(existingToken)).thenReturn(existingToken);

        PushTokenResponse response = pushTokenService.registerToken(1L, request);

        assertNotNull(response);
        assertEquals(PushPlatform.IOS, existingToken.getPlatform());
        assertNotNull(existingToken.getLastUsedAt());
        verify(pushTokenRepository, times(1)).save(existingToken);
    }

    @Test
    void registerToken_DeveReatribuirTokenParaNovoUsuario_QuandoTokenJaExistiaParaOutroUsuario() {
        AppUser outroUsuario = new AppUser();
        outroUsuario.setId(2L);

        PushTokenRequest request = new PushTokenRequest("fcm-token-trocado", PushPlatform.ANDROID);
        PushToken existingToken = new PushToken(outroUsuario, "fcm-token-trocado", PushPlatform.WEB);
        existingToken.setId(30L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(appUser));
        when(pushTokenRepository.findByToken("fcm-token-trocado")).thenReturn(Optional.of(existingToken));
        when(pushTokenRepository.save(existingToken)).thenReturn(existingToken);

        PushTokenResponse response = pushTokenService.registerToken(1L, request);

        assertNotNull(response);
        assertEquals(appUser, existingToken.getUser());
        assertEquals(PushPlatform.ANDROID, existingToken.getPlatform());
        verify(pushTokenRepository, times(1)).save(existingToken);
    }

    @Test
    void registerToken_DeveLancarEntityNotFoundException_QuandoUsuarioNaoExistir() {
        PushTokenRequest request = new PushTokenRequest("fcm-token-123", PushPlatform.ANDROID);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> pushTokenService.registerToken(99L, request));
        verify(pushTokenRepository, never()).save(any());
    }

    @Test
    void registerToken_DeveLancarIllegalArgumentException_QuandoUserIdNuloOuTokenEmBranco() {
        PushTokenRequest request = new PushTokenRequest("token", PushPlatform.ANDROID);
        assertThrows(IllegalArgumentException.class, () -> pushTokenService.registerToken(null, request));

        PushTokenRequest invalidRequest = new PushTokenRequest("   ", PushPlatform.ANDROID);
        assertThrows(IllegalArgumentException.class, () -> pushTokenService.registerToken(1L, invalidRequest));
    }

    @Test
    void revokeToken_DeveExcluirToken_QuandoTokenPertencerAoUsuario() {
        String token = "fcm-token-revogar";
        PushToken tokenEntity = new PushToken(appUser, token, PushPlatform.ANDROID);

        when(pushTokenRepository.findByTokenAndUserId(token, 1L)).thenReturn(Optional.of(tokenEntity));

        pushTokenService.revokeToken(1L, token);

        verify(pushTokenRepository, times(1)).delete(tokenEntity);
    }

    @Test
    void revokeToken_DeveSerIdempotenteENaoFalhar_QuandoTokenNaoPertencerAoUsuarioOuNaoExistir() {
        String token = "fcm-token-alheio";
        when(pushTokenRepository.findByTokenAndUserId(token, 1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> pushTokenService.revokeToken(1L, token));
        verify(pushTokenRepository, never()).delete(any());
    }

    @Test
    void revokeToken_DeveIgnorar_QuandoTokenForNuloOuVazio() {
        assertDoesNotThrow(() -> pushTokenService.revokeToken(1L, null));
        assertDoesNotThrow(() -> pushTokenService.revokeToken(1L, "   "));
        assertDoesNotThrow(() -> pushTokenService.revokeToken(null, "token"));
        verifyNoInteractions(pushTokenRepository);
    }

    @Test
    void getTokensByUser_DeveRetornarListaDeTokens() {
        PushToken token1 = new PushToken(appUser, "tok-1", PushPlatform.ANDROID);
        PushToken token2 = new PushToken(appUser, "tok-2", PushPlatform.IOS);

        when(pushTokenRepository.findByUserId(1L)).thenReturn(List.of(token1, token2));

        List<PushTokenResponse> responses = pushTokenService.getTokensByUser(1L);

        assertEquals(2, responses.size());
        assertEquals("tok-1", responses.get(0).token());
        assertEquals("tok-2", responses.get(1).token());
    }

    @Test
    void getTokensByUser_DeveRetornarListaVazia_QuandoUserIdNulo() {
        List<PushTokenResponse> responses = pushTokenService.getTokensByUser(null);
        assertTrue(responses.isEmpty());
        verifyNoInteractions(pushTokenRepository);
    }
}
