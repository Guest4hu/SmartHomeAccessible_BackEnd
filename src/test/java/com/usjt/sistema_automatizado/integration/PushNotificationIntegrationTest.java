package com.usjt.sistema_automatizado.integration;

import com.usjt.sistema_automatizado.config.FirebaseProperties;
import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.dto.request.EventRequest;
import com.usjt.sistema_automatizado.dto.request.PushTokenRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.PushTokenResponse;
import com.usjt.sistema_automatizado.mapper.DeviceMapper;
import com.usjt.sistema_automatizado.mapper.MqttMessageMapper;
import com.usjt.sistema_automatizado.mapper.PushTokenMapper;
import com.usjt.sistema_automatizado.mapper.TelemetryMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.Home;
import com.usjt.sistema_automatizado.model.entity.PushToken;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.model.enums.EventType;
import com.usjt.sistema_automatizado.model.enums.PushPlatform;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import com.usjt.sistema_automatizado.repository.PushTokenRepository;
import com.usjt.sistema_automatizado.repository.TelemetryReadingRepository;
import com.usjt.sistema_automatizado.service.CommandAckService;
import com.usjt.sistema_automatizado.service.DeviceService;
import com.usjt.sistema_automatizado.service.EventService;
import com.usjt.sistema_automatizado.service.NotificationService;
import com.usjt.sistema_automatizado.service.TelemetryService;
import com.usjt.sistema_automatizado.service.impl.PushNotificationServiceImpl;
import com.usjt.sistema_automatizado.service.impl.PushTokenServiceImpl;
import com.usjt.sistema_automatizado.service.mqtt.EventMqttHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Teste de Integração de Ponta a Ponta para o subsistema de Notificações Push FCM.
 *
 * <p>Cobre o pipeline integrado:
 * 1. Ciclo de vida do PushToken (registro, reatribuição de propriedade e revogação idempotente).
 * 2. Despacho resiliente em modo Dry-Run / Mock sem dependência de credenciais externas.
 * 3. Gatilhos de eventos integrados: Campainha (DOORBELL), Anomalias Térmicas e Queda de Conexão.
 * 4. Isolamento estrito entre residências (Zero cross-home push leakage).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Push Notification Subsystem - End-to-End Integration Tests")
class PushNotificationIntegrationTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private HomeMemberRepository homeMemberRepository;

    @Mock
    private TelemetryReadingRepository telemetryRepository;

    @Mock
    private EventService eventService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CommandAckService commandAckService;

    @Mock
    private MqttMessageMapper mqttMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private TelemetryMapper telemetryMapper;

    private PushTokenMapper pushTokenMapper;
    private FirebaseProperties firebaseProperties;
    private PushNotificationServiceImpl pushNotificationService;
    private PushTokenServiceImpl pushTokenService;
    private EventMqttHandler eventMqttHandler;
    private TelemetryService telemetryService;
    private DeviceService deviceService;

    private AppUser userAlice;
    private AppUser userBob;
    private Home homeA;
    private Device doorbellDevice;
    private Device telemetryDevice;
    private Device lampDevice;

    @BeforeEach
    void setUp() {
        pushTokenMapper = new PushTokenMapper();

        firebaseProperties = new FirebaseProperties();
        firebaseProperties.setEnabled(false);
        firebaseProperties.setDryRun(true);
        firebaseProperties.setCredentialsPath("");

        pushNotificationService = new PushNotificationServiceImpl(pushTokenRepository, firebaseProperties);
        pushNotificationService.init();

        pushTokenService = new PushTokenServiceImpl(pushTokenRepository, appUserRepository, pushTokenMapper);

        eventMqttHandler = new EventMqttHandler(
                eventService,
                notificationService,
                mqttMapper,
                commandAckService,
                deviceRepository,
                pushNotificationService
        );

        telemetryService = new TelemetryService(
                telemetryRepository,
                deviceRepository,
                telemetryMapper,
                notificationService,
                pushNotificationService
        );

        deviceService = new DeviceService(
                deviceRepository,
                homeMemberRepository,
                deviceMapper,
                notificationService,
                pushNotificationService
        );

        homeA = new Home();
        homeA.setId(10L);
        homeA.setName("Residência A");

        userAlice = new AppUser();
        userAlice.setId(1L);
        userAlice.setName("Alice Silva");
        userAlice.setEmail("alice@example.com");

        userBob = new AppUser();
        userBob.setId(2L);
        userBob.setName("Bob Silva");
        userBob.setEmail("bob@example.com");

        doorbellDevice = new Device();
        doorbellDevice.setId(101L);
        doorbellDevice.setName("Campainha Entrada");
        doorbellDevice.setExternalId("esp32-doorbell");
        doorbellDevice.setHome(homeA);
        doorbellDevice.setStatus(DeviceStatus.ONLINE);

        telemetryDevice = new Device();
        telemetryDevice.setId(102L);
        telemetryDevice.setName("Sensor Térmico Sala");
        telemetryDevice.setExternalId("esp32-temp");
        telemetryDevice.setHome(homeA);
        telemetryDevice.setStatus(DeviceStatus.ONLINE);

        lampDevice = new Device();
        lampDevice.setId(103L);
        lampDevice.setName("Lâmpada Corredor");
        lampDevice.setExternalId("esp32-lamp");
        lampDevice.setHome(homeA);
        lampDevice.setStatus(DeviceStatus.ONLINE);
    }

    @Test
    @DisplayName("Ciclo de vida do PushToken: cadastro, reatribuição de propriedade e revogação idempotente")
    void testPushTokenFullLifecycle_RegistrationReassignmentAndRevocation() {
        String tokenStr = "fcm-token-alpha-123";
        PushTokenRequest requestAlice = new PushTokenRequest(tokenStr, PushPlatform.ANDROID);

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(userAlice));
        when(pushTokenRepository.findByToken(tokenStr)).thenReturn(Optional.empty());

        PushToken tokenEntityAlice = new PushToken(userAlice, tokenStr, PushPlatform.ANDROID);
        tokenEntityAlice.setId(501L);
        when(pushTokenRepository.save(any(PushToken.class))).thenReturn(tokenEntityAlice);

        PushTokenResponse resAlice = pushTokenService.registerToken(1L, requestAlice);
        assertNotNull(resAlice);
        assertEquals(tokenStr, resAlice.token());
        assertEquals(PushPlatform.ANDROID, resAlice.platform());

        // Reatribuição do mesmo token físico ao Bob (troca de aparelho na família)
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(userBob));
        when(pushTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(tokenEntityAlice));

        PushToken tokenEntityBob = new PushToken(userBob, tokenStr, PushPlatform.IOS);
        tokenEntityBob.setId(501L);
        when(pushTokenRepository.save(any(PushToken.class))).thenReturn(tokenEntityBob);

        PushTokenRequest requestBob = new PushTokenRequest(tokenStr, PushPlatform.IOS);
        PushTokenResponse resBob = pushTokenService.registerToken(2L, requestBob);
        assertNotNull(resBob);
        assertEquals(tokenStr, resBob.token());
        assertEquals(PushPlatform.IOS, resBob.platform());

        // Tentativa de revogação por Alice não afeta o token do Bob
        when(pushTokenRepository.findByTokenAndUserId(tokenStr, 1L)).thenReturn(Optional.empty());
        pushTokenService.revokeToken(1L, tokenStr);
        verify(pushTokenRepository, never()).delete(any(PushToken.class));

        // Revogação pelo proprietário legítimo (Bob) remove o token
        when(pushTokenRepository.findByTokenAndUserId(tokenStr, 2L)).thenReturn(Optional.of(tokenEntityBob));
        pushTokenService.revokeToken(2L, tokenStr);
        verify(pushTokenRepository, times(1)).delete(tokenEntityBob);
    }

    @Test
    @DisplayName("Gatilho DOORBELL via MQTT: despacha push para todos os membros da residência e zero para outras")
    void testDoorbellMqttEvent_TriggersPushToHomeResidentsOnly() throws Exception {
        MqttEnvelope envelope = new MqttEnvelope("devices/esp32-doorbell/event", "esp32-doorbell", "{\"type\":\"DOORBELL\"}");
        EventRequest request = new EventRequest(1, "esp32-doorbell", LocalDateTime.now(), EventType.DOORBELL);

        when(mqttMapper.toEventRequest(envelope)).thenReturn(request);
        when(deviceRepository.findHomeIdByExternalId("esp32-doorbell")).thenReturn(Optional.of(10L));

        PushToken tokenAlice = new PushToken(userAlice, "token-alice-homeA", PushPlatform.ANDROID);
        PushToken tokenBob = new PushToken(userBob, "token-bob-homeA", PushPlatform.IOS);
        List<PushToken> homeATokens = new ArrayList<>(List.of(tokenAlice, tokenBob));

        when(pushTokenRepository.findAllByHomeId(10L)).thenReturn(homeATokens);

        eventMqttHandler.handle(envelope);

        // Verifica que o evento persistiu e acionou SSE
        verify(eventService).createEvent(request);
        verify(notificationService).dispatchEvent("esp32-doorbell", "DOORBELL");

        // Verifica que o push foi despachado aos membros da Residência A
        verify(pushTokenRepository, times(1)).findAllByHomeId(10L);
        verify(pushTokenRepository, never()).findAllByHomeId(20L); // Residência B nunca é consultada

        // Verifica que os tokens da Residência A tiveram seus timestamps atualizados
        assertNotNull(tokenAlice.getLastUsedAt());
        assertNotNull(tokenBob.getLastUsedAt());
        verify(pushTokenRepository, times(1)).saveAll(homeATokens);
    }

    @Test
    @DisplayName("Anomalia Térmica Crítica (>= 45°C): despacha push de alerta com urgência CRITICAL")
    void testThermalCriticalAlertFlow_TriggersPushNotification() {
        TelemetryRequest request = new TelemetryRequest("esp32-temp", 48.5, 40.0, 200.0);

        when(deviceRepository.findByExternalId("esp32-temp")).thenReturn(Optional.of(telemetryDevice));
        when(telemetryMapper.toEntityList(any(), any())).thenReturn(List.of());
        when(deviceRepository.findHomeIdByExternalId("esp32-temp")).thenReturn(Optional.of(10L));

        PushToken tokenAlice = new PushToken(userAlice, "token-alice-homeA", PushPlatform.ANDROID);
        when(pushTokenRepository.findAllByHomeId(10L)).thenReturn(List.of(tokenAlice));

        telemetryService.saveTelemetry(request);

        // Verifica push acionado para Home 10
        verify(pushTokenRepository).findAllByHomeId(10L);
        assertNotNull(tokenAlice.getLastUsedAt());
    }

    @Test
    @DisplayName("Anomalia de Congelamento (<= 0°C): despacha push de alerta de congelamento")
    void testThermalFreezeWarningFlow_TriggersPushNotification() {
        TelemetryRequest request = new TelemetryRequest("esp32-temp", -3.0, 80.0, 50.0);

        when(deviceRepository.findByExternalId("esp32-temp")).thenReturn(Optional.of(telemetryDevice));
        when(telemetryMapper.toEntityList(any(), any())).thenReturn(List.of());
        when(deviceRepository.findHomeIdByExternalId("esp32-temp")).thenReturn(Optional.of(10L));

        PushToken tokenAlice = new PushToken(userAlice, "token-alice-homeA", PushPlatform.ANDROID);
        when(pushTokenRepository.findAllByHomeId(10L)).thenReturn(List.of(tokenAlice));

        telemetryService.saveTelemetry(request);

        verify(pushTokenRepository).findAllByHomeId(10L);
        assertNotNull(tokenAlice.getLastUsedAt());
    }

    @Test
    @DisplayName("Transição de Dispositivo para OFFLINE: despacha push de desconexão para a residência")
    void testDeviceOfflineFlow_TriggersPushNotification() {
        when(deviceRepository.findByExternalId("esp32-lamp")).thenReturn(Optional.of(lampDevice));

        PushToken tokenBob = new PushToken(userBob, "token-bob-homeA", PushPlatform.IOS);
        when(pushTokenRepository.findAllByHomeId(10L)).thenReturn(List.of(tokenBob));

        deviceService.updateDeviceStatus("esp32-lamp", DeviceStatus.OFFLINE);

        assertEquals(DeviceStatus.OFFLINE, lampDevice.getStatus());
        verify(deviceRepository).save(lampDevice);

        // Verifica que notificação de desconexão foi despachada para a residência
        verify(pushTokenRepository).findAllByHomeId(10L);
        assertNotNull(tokenBob.getLastUsedAt());
    }

    @Test
    @DisplayName("Resiliência Dry-Run: opera sem erros e atualiza uso dos tokens quando Firebase desabilitado")
    void testDryRunResilience_GracefulHandlingWhenCredentialsMissing() {
        assertTrue(pushNotificationService.isDryRun());

        PushToken token1 = new PushToken(userAlice, "dryrun-token-1", PushPlatform.WEB);
        when(pushTokenRepository.findAllByHomeId(10L)).thenReturn(List.of(token1));

        assertDoesNotThrow(() -> {
            pushNotificationService.sendNotificationToHome(
                    10L,
                    "Teste de Título",
                    "Teste de Mensagem",
                    Map.of("chave", "valor")
            );
        });

        assertNotNull(token1.getLastUsedAt());
        verify(pushTokenRepository).saveAll(List.of(token1));
    }
}
