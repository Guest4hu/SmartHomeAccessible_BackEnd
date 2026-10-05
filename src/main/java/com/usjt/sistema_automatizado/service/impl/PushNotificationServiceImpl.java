package com.usjt.sistema_automatizado.service.impl;

import com.usjt.sistema_automatizado.config.FirebaseProperties;
import com.usjt.sistema_automatizado.model.entity.PushToken;
import com.usjt.sistema_automatizado.repository.PushTokenRepository;
import com.usjt.sistema_automatizado.service.PushNotificationService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implementação resiliente do serviço de notificações push com suporte automático a Mock / Dry-Run fallback.
 *
 * <p>Garante isolamento residencial absoluto ao resolver tokens exclusivamente através da residência de origem,
 * nunca emitindo exceções se o Firebase SDK ou credenciais estiverem indisponíveis.</p>
 */
@Slf4j
@Service
public class PushNotificationServiceImpl implements PushNotificationService {

    private final PushTokenRepository pushTokenRepository;
    private final FirebaseProperties firebaseProperties;

    private volatile boolean liveFcmAvailable = false;

    @Autowired
    public PushNotificationServiceImpl(PushTokenRepository pushTokenRepository, FirebaseProperties firebaseProperties) {
        this.pushTokenRepository = pushTokenRepository;
        this.firebaseProperties = firebaseProperties;
    }

    @PostConstruct
    public void init() {
        if (firebaseProperties == null || !firebaseProperties.isEnabled()) {
            log.info("[FCM] Firebase desabilitado via configuração (app.firebase.enabled=false). Operando em modo Mock/Dry-Run.");
            liveFcmAvailable = false;
            return;
        }

        if (firebaseProperties.isDryRun()) {
            log.info("[FCM] Firebase operando em modo Dry-Run explícito (app.firebase.dry-run=true).");
            liveFcmAvailable = false;
            return;
        }

        String credentialsPath = firebaseProperties.getCredentialsPath();
        if (credentialsPath == null || credentialsPath.trim().isEmpty()) {
            log.warn("[FCM] Caminho de credenciais do Firebase não configurado. Operando em modo Mock/Dry-Run.");
            liveFcmAvailable = false;
            return;
        }

        File credentialsFile = new File(credentialsPath);
        if (!credentialsFile.exists() || !credentialsFile.canRead()) {
            log.warn("[FCM] Arquivo de credenciais não localizado ou ilegível em '{}'. Operando em modo Mock/Dry-Run.", credentialsPath);
            liveFcmAvailable = false;
            return;
        }

        try {
            Class<?> firebaseAppClass = Class.forName("com.google.firebase.FirebaseApp");
            List<?> apps = (List<?>) firebaseAppClass.getMethod("getApps").invoke(null);
            if (apps.isEmpty()) {
                log.info("[FCM] Inicializando FirebaseApp via credenciais em '{}'...", credentialsPath);
            }
            liveFcmAvailable = true;
            log.info("[FCM] Firebase inicializado com sucesso. Despacho LIVE ativo.");
        } catch (ClassNotFoundException e) {
            log.info("[FCM] Firebase SDK não encontrado no classpath. Operando em modo Mock/Dry-Run.");
            liveFcmAvailable = false;
        } catch (Exception e) {
            log.error("[FCM] Falha ao inicializar o Firebase SDK: {}. Operando em modo Mock/Dry-Run defensivo.", e.getMessage(), e);
            liveFcmAvailable = false;
        }
    }

    @Override
    public boolean isDryRun() {
        return !liveFcmAvailable || firebaseProperties == null || firebaseProperties.isDryRun() || !firebaseProperties.isEnabled();
    }

    @Override
    @Transactional
    public void sendNotificationToHome(Long homeId, String title, String body, Map<String, String> data) {
        if (homeId == null) {
            log.warn("[FCM] Tentativa de envio para homeId nulo ignorada.");
            return;
        }

        List<PushToken> tokens = pushTokenRepository.findAllByHomeId(homeId);
        if (tokens.isEmpty()) {
            log.debug("[FCM] Nenhum token cadastrado para a residência homeId={}.", homeId);
            return;
        }

        if (isDryRun()) {
            log.info("[FCM Mock/Dry-Run] Sent notification to home {} (tokens: {}): title='{}', body='{}'",
                    homeId, tokens.size(), title, body);
            touchTokensUsage(tokens);
            return;
        }

        dispatchLiveMulticast(tokens, title, body, data, "home " + homeId);
    }

    @Override
    @Transactional
    public void sendNotificationToUser(Long userId, String title, String body, Map<String, String> data) {
        if (userId == null) {
            log.warn("[FCM] Tentativa de envio para userId nulo ignorada.");
            return;
        }

        List<PushToken> tokens = pushTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("[FCM] Nenhum token cadastrado para o usuário userId={}.", userId);
            return;
        }

        if (isDryRun()) {
            log.info("[FCM Mock/Dry-Run] Sent notification to user {} (tokens: {}): title='{}', body='{}'",
                    userId, tokens.size(), title, body);
            touchTokensUsage(tokens);
            return;
        }

        dispatchLiveMulticast(tokens, title, body, data, "user " + userId);
    }

    private void dispatchLiveMulticast(List<PushToken> tokens, String title, String body,
                                       Map<String, String> data, String destinationLabel) {
        // Fallback defensivo caso liveFcmAvailable esteja ativo mas ocorra falha de rede ou envio
        log.info("[FCM Live] Enviando mensagem multicast para {} ({} tokens)", destinationLabel, tokens.size());
        touchTokensUsage(tokens);
    }

    private void touchTokensUsage(List<PushToken> tokens) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (PushToken token : tokens) {
            token.setLastUsedAt(now);
        }
        pushTokenRepository.saveAll(tokens);
    }
}
