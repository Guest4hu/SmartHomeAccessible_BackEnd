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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    private FirebaseProperties firebaseProperties;
    private PushNotificationServiceImpl pushNotificationService;

    private PushToken token1;
    private PushToken token2;

    @BeforeEach
    void setUp() {
        firebaseProperties = new FirebaseProperties();
        firebaseProperties.setEnabled(false);
        firebaseProperties.setDryRun(true);
        firebaseProperties.setCredentialsPath("");

        pushNotificationService = new PushNotificationServiceImpl(pushTokenRepository, firebaseProperties);

        AppUser user1 = new AppUser();
        user1.setId(10L);

        token1 = new PushToken(user1, "fcm-token-alpha", PushPlatform.ANDROID);
        token1.setId(1L);

        token2 = new PushToken(user1, "fcm-token-beta", PushPlatform.IOS);
        token2.setId(2L);
    }

    @Test
    @DisplayName("Deve enviar notificação para a residência em modo Dry-Run atualizando lastUsedAt")
    void sendNotificationToHome_DeveProcessarEmModoDryRunEAtualizarLastUsedAt() {
        Long homeId = 1L;
        when(pushTokenRepository.findAllByHomeId(homeId)).thenReturn(List.of(token1, token2));

        pushNotificationService.sendNotificationToHome(homeId, "Campainha", "Tocando", Map.of("eventType", "DOORBELL"));

        verify(pushTokenRepository, times(1)).findAllByHomeId(homeId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(pushTokenRepository, times(1)).saveAll(captor.capture());

        List<PushToken> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertNotNull(saved.get(0).getLastUsedAt());
        assertNotNull(saved.get(1).getLastUsedAt());
    }

    @Test
    @DisplayName("Deve ignorar envio quando residência não possui tokens cadastrados")
    void sendNotificationToHome_NaoDeveFalhar_QuandoCasaNaoPossuiTokens() {
        Long homeId = 99L;
        when(pushTokenRepository.findAllByHomeId(homeId)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() ->
                pushNotificationService.sendNotificationToHome(homeId, "Alerta", "Corpo", Map.of())
        );

        verify(pushTokenRepository, times(1)).findAllByHomeId(homeId);
        verify(pushTokenRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Deve enviar notificação para usuário específico em modo Dry-Run")
    void sendNotificationToUser_DeveEnviarParaTokensDoUsuario_EmModoDryRun() {
        Long userId = 10L;
        when(pushTokenRepository.findByUserId(userId)).thenReturn(List.of(token1));

        pushNotificationService.sendNotificationToUser(userId, "Mensagem", "Corpo", Map.of());

        verify(pushTokenRepository, times(1)).findByUserId(userId);
        verify(pushTokenRepository, times(1)).saveAll(any());
        assertNotNull(token1.getLastUsedAt());
    }

    @Test
    @DisplayName("Deve ignorar envio quando usuário não possui tokens cadastrados")
    void sendNotificationToUser_NaoDeveFalhar_QuandoUsuarioNaoPossuiTokens() {
        Long userId = 88L;
        when(pushTokenRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() ->
                pushNotificationService.sendNotificationToUser(userId, "Mensagem", "Corpo", Map.of())
        );

        verify(pushTokenRepository, times(1)).findByUserId(userId);
        verify(pushTokenRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Deve retornar true para isDryRun quando não configurado")
    void isDryRun_DeveRetornarTrue_QuandoConfiguradoComoDryRunOuSemCredenciais() {
        assertTrue(pushNotificationService.isDryRun());
    }

    @Test
    @DisplayName("Deve garantir isolamento residencial consultando estritamente findAllByHomeId")
    void sendNotificationToHome_DeveGarantirIsolamentoResidencial() {
        Long home1 = 5L;
        when(pushTokenRepository.findAllByHomeId(home1)).thenReturn(List.of(token1));

        pushNotificationService.sendNotificationToHome(home1, "Título", "Corpo", Map.of());

        verify(pushTokenRepository, times(1)).findAllByHomeId(home1);
        verify(pushTokenRepository, never()).findAllByHomeId(eq(1L));
        verify(pushTokenRepository, never()).findAll();
    }

    @Test
    @DisplayName("Deve operar defensivamente e não lançar exceção para homeId ou userId nulo")
    void sendNotification_DeveIgnorarQuandoIdentificadoresForemNulos() {
        pushNotificationService.sendNotificationToHome(null, "Título", "Corpo", Map.of());
        pushNotificationService.sendNotificationToUser(null, "Título", "Corpo", Map.of());

        verifyNoInteractions(pushTokenRepository);
    }

    @Test
    @DisplayName("Deve despachar notificação de evento do sistema via sendEventNotification")
    void sendEventNotification_DeveMontarPayloadEConsultarHome() {
        Long homeId = 1L;
        when(pushTokenRepository.findAllByHomeId(homeId)).thenReturn(List.of(token1));

        pushNotificationService.sendEventNotification(homeId, "DOORBELL", "esp32-01", "Campainha", "Tocando");

        verify(pushTokenRepository, times(1)).findAllByHomeId(homeId);
        verify(pushTokenRepository, times(1)).saveAll(any());
    }
}

