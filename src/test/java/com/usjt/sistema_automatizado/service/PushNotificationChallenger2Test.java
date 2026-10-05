package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.config.FirebaseProperties;
import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.dto.request.EventRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.mapper.DeviceMapper;
import com.usjt.sistema_automatizado.mapper.MqttMessageMapper;
import com.usjt.sistema_automatizado.mapper.TelemetryMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.Home;
import com.usjt.sistema_automatizado.model.entity.PushToken;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.model.enums.EventType;
import com.usjt.sistema_automatizado.model.enums.PushPlatform;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import com.usjt.sistema_automatizado.repository.PushTokenRepository;
import com.usjt.sistema_automatizado.repository.TelemetryReadingRepository;
import com.usjt.sistema_automatizado.service.impl.PushNotificationServiceImpl;
import com.usjt.sistema_automatizado.service.mqtt.EventMqttHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Challenger 2 Empirical Test Suite:
 * - Multi-tenant residential isolation and zero token leakage
 * - Mock/Dry-Run fallback resiliency across diverse configuration permutations
 * - Comprehensive event-to-push routing (DOORBELL, thermal boundary conditions, device offline transitions)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Challenger 2 Empirical Verification: Multi-Tenancy, Fallback & Event Triggers")
class PushNotificationChallenger2Test {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private HomeMemberRepository homeMemberRepository;

    @Mock
    private TelemetryReadingRepository telemetryRepository;

    @Mock
    private TelemetryMapper telemetryMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EventService eventService;

    @Mock
    private MqttMessageMapper mqttMapper;

    @Mock
    private CommandAckService commandAckService;

    @Nested
    @DisplayName("1. Multi-Tenant Residential Boundary Isolation & Zero Leakage")
    class ResidentialIsolationTests {

        @Test
        @DisplayName("Zero Leakage: Despacho para Casa 100 nunca toca tokens da Casa 200")
        void sendNotificationToHome_ZeroLeakageBetweenHomes() {
            Long homeA = 100L;
            Long homeB = 200L;

            AppUser userA1 = new AppUser();
            userA1.setId(1L);
            PushToken tokenA1 = new PushToken(userA1, "token-home-a-1", PushPlatform.ANDROID);
            tokenA1.setId(10L);

            AppUser userA2 = new AppUser();
            userA2.setId(2L);
            PushToken tokenA2 = new PushToken(userA2, "token-home-a-2", PushPlatform.IOS);
            tokenA2.setId(11L);

            AppUser userB1 = new AppUser();
            userB1.setId(3L);
            PushToken tokenB1 = new PushToken(userB1, "token-home-b-1", PushPlatform.WEB);
            tokenB1.setId(20L);

            when(pushTokenRepository.findAllByHomeId(homeA)).thenReturn(List.of(tokenA1, tokenA2));

            FirebaseProperties properties = new FirebaseProperties();
            properties.setEnabled(false);
            properties.setDryRun(true);

            PushNotificationServiceImpl service = new PushNotificationServiceImpl(pushTokenRepository, properties);

            // Act: Dispatch to Home A
            service.sendNotificationToHome(homeA, "Alerta Casa A", "Mensagem A", Map.of("home", "100"));

            // Assert
            verify(pushTokenRepository, times(1)).findAllByHomeId(homeA);
            verify(pushTokenRepository, never()).findAllByHomeId(homeB);
            verify(pushTokenRepository, never()).findAll();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<PushToken>> captor = ArgumentCaptor.forClass(List.class);
            verify(pushTokenRepository, times(1)).saveAll(captor.capture());

            List<PushToken> saved = captor.getValue();
            assertEquals(2, saved.size());
            assertTrue(saved.contains(tokenA1));
            assertTrue(saved.contains(tokenA2));
            assertFalse(saved.contains(tokenB1), "CRITICAL: Token da Casa B nunca deve vazar para a Casa A!");

            assertNotNull(tokenA1.getLastUsedAt());
            assertNotNull(tokenA2.getLastUsedAt());
            assertNull(tokenB1.getLastUsedAt(), "Token da Casa B deve permanecer com lastUsedAt intacto");
        }

        @Test
        @DisplayName("Residência sem tokens cadastrados: não executa saveAll nem propaga erro")
        void sendNotificationToHome_EmptyTokens_GracefulNoOp() {
            when(pushTokenRepository.findAllByHomeId(999L)).thenReturn(Collections.emptyList());

            FirebaseProperties properties = new FirebaseProperties();
            PushNotificationServiceImpl service = new PushNotificationServiceImpl(pushTokenRepository, properties);

            assertDoesNotThrow(() -> service.sendNotificationToHome(999L, "Titulo", "Corpo", Map.of()));

            verify(pushTokenRepository, times(1)).findAllByHomeId(999L);
            verify(pushTokenRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Identificador homeId nulo: ignora silenciosamente sem consultar repositório")
        void sendNotificationToHome_NullHomeId_IgnoredSafely() {
            FirebaseProperties properties = new FirebaseProperties();
            PushNotificationServiceImpl service = new PushNotificationServiceImpl(pushTokenRepository, properties);

            assertDoesNotThrow(() -> service.sendNotificationToHome(null, "Titulo", "Corpo", Map.of()));

            verifyNoInteractions(pushTokenRepository);
        }
    }

    @Nested
    @DisplayName("2. Mock/Dry-Run Fallback Resiliency Permutations")
    class FallbackResiliencyTests {

        @Test
        @DisplayName("Credenciais nulas: opera em Dry-Run sem exceção")
        void init_CredentialsPathNull_FallsBackToDryRun() {
            FirebaseProperties properties = new FirebaseProperties();
            properties.setEnabled(true);
            properties.setDryRun(false);
            properties.setCredentialsPath(null);

            PushNotificationServiceImpl service = new PushNotificationServiceImpl(pushTokenRepository, properties);
            assertDoesNotThrow(service::init);
            assertTrue(service.isDryRun());
        }

        @Test
        @DisplayName("Credenciais vazias ou apenas espaços: opera em Dry-Run sem exceção")
        void init_CredentialsPathBlank_FallsBackToDryRun() {
            FirebaseProperties properties = new FirebaseProperties();
            properties.setEnabled(true);
            properties.setDryRun(false);
            properties.setCredentialsPath("   \t  ");

            PushNotificationServiceImpl service = new PushNotificationServiceImpl(pushTokenRepository, properties);
            assertDoesNotThrow(service::init);
            assertTrue(service.isDryRun());
        }

        @Test
        @DisplayName("Arquivo de credenciais inexistente no disco: opera em Dry-Run sem lançar FileNotFoundException")
        void init_CredentialsFileNotFound_FallsBackToDryRun() {
            FirebaseProperties properties = new FirebaseProperties();
            properties.setEnabled(true);
            properties.setDryRun(false);
            properties.setCredentialsPath("/nonexistent/path/firebase_service_account_credentials.json");

            PushNotificationServiceImpl service = new PushNotificationServiceImpl(pushTokenRepository, properties);
            assertDoesNotThrow(service::init);
            assertTrue(service.isDryRun());
        }

        @Test
        @DisplayName("FirebaseProperties nulo: opera defensivamente em Dry-Run sem lançar NPE")
        void init_NullProperties_HandlesGracefully() {
            PushNotificationServiceImpl service = new PushNotificationServiceImpl(pushTokenRepository, null);
            assertDoesNotThrow(service::init);
            assertTrue(service.isDryRun());

            PushToken token = new PushToken(new AppUser(), "tok-1", PushPlatform.ANDROID);
            when(pushTokenRepository.findAllByHomeId(10L)).thenReturn(List.of(token));

            assertDoesNotThrow(() -> service.sendNotificationToHome(10L, "Teste", "Msg", null));
            assertNotNull(token.getLastUsedAt());
            verify(pushTokenRepository, times(1)).saveAll(List.of(token));
        }

        @Test
        @DisplayName("Payload com title ou body nulo: executa atualização de token em Dry-Run sem quebrar")
        void sendNotificationToHome_NullTitleAndBody_DoesNotThrow() {
            FirebaseProperties properties = new FirebaseProperties();
            PushNotificationServiceImpl service = new PushNotificationServiceImpl(pushTokenRepository, properties);

            PushToken token = new PushToken(new AppUser(), "tok-null-payload", PushPlatform.IOS);
            when(pushTokenRepository.findAllByHomeId(5L)).thenReturn(List.of(token));

            assertDoesNotThrow(() -> service.sendNotificationToHome(5L, null, null, null));
            assertNotNull(token.getLastUsedAt());
            verify(pushTokenRepository, times(1)).saveAll(List.of(token));
        }
    }

    @Nested
    @DisplayName("3. Event Triggers: DOORBELL, Thermal Boundaries, and Device Offline")
    class EventTriggersTests {

        @Test
        @DisplayName("DOORBELL Event: Dispara push notification para a residência associada ao hardware")
        void doorbellEvent_DispatchesPushToResolvedHome() throws Exception {
            PushNotificationService mockPush = mock(PushNotificationService.class);
            EventMqttHandler handler = new EventMqttHandler(
                    eventService, notificationService, mqttMapper, commandAckService, deviceRepository, mockPush
            );

            MqttEnvelope envelope = new MqttEnvelope("devices/esp32-bell/event", "esp32-bell", "{\"type\":\"DOORBELL\"}");
            EventRequest request = new EventRequest(1, "esp32-bell", LocalDateTime.now(), EventType.DOORBELL);

            when(mqttMapper.toEventRequest(envelope)).thenReturn(request);
            when(deviceRepository.findHomeIdByExternalId("esp32-bell")).thenReturn(Optional.of(42L));

            handler.handle(envelope);

            verify(mockPush, times(1)).sendNotificationToHome(
                    eq(42L),
                    eq("Campainha Acionada"),
                    contains("esp32-bell"),
                    argThat(map -> "DOORBELL".equals(map.get("eventType")) && "WARNING".equals(map.get("urgency")))
            );
        }

        @Test
        @DisplayName("DOORBELL Event em dispositivo sem residência vinculada: não quebra e não dispara push")
        void doorbellEvent_OrphanDevice_DoesNotThrowOrDispatchPush() throws Exception {
            PushNotificationService mockPush = mock(PushNotificationService.class);
            EventMqttHandler handler = new EventMqttHandler(
                    eventService, notificationService, mqttMapper, commandAckService, deviceRepository, mockPush
            );

            MqttEnvelope envelope = new MqttEnvelope("devices/esp32-unbound/event", "esp32-unbound", "{\"type\":\"DOORBELL\"}");
            EventRequest request = new EventRequest(1, "esp32-unbound", LocalDateTime.now(), EventType.DOORBELL);

            when(mqttMapper.toEventRequest(envelope)).thenReturn(request);
            when(deviceRepository.findHomeIdByExternalId("esp32-unbound")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> handler.handle(envelope));
            verifyNoInteractions(mockPush);
        }

        @Test
        @DisplayName("Evento não-DOORBELL (ex: PRESENCE_DETECTED): não aciona push móvel")
        void presenceDetectedEvent_DoesNotDispatchPush() throws Exception {
            PushNotificationService mockPush = mock(PushNotificationService.class);
            EventMqttHandler handler = new EventMqttHandler(
                    eventService, notificationService, mqttMapper, commandAckService, deviceRepository, mockPush
            );

            MqttEnvelope envelope = new MqttEnvelope("devices/esp32-pir/event", "esp32-pir", "{\"type\":\"PRESENCE_DETECTED\"}");
            EventRequest request = new EventRequest(1, "esp32-pir", LocalDateTime.now(), EventType.PRESENCE_DETECTED);

            when(mqttMapper.toEventRequest(envelope)).thenReturn(request);

            handler.handle(envelope);

            verifyNoInteractions(mockPush);
        }

        @Test
        @DisplayName("Limite Térmico Crítico: 45.0 °C dispara push notification CRITICAL")
        void thermalBoundary_45Degrees_DispatchesCriticalPush() {
            PushNotificationService mockPush = mock(PushNotificationService.class);
            TelemetryService telemetryService = new TelemetryService(
                    telemetryRepository, deviceRepository, telemetryMapper, notificationService, mockPush
            );

            Device device = new Device();
            device.setId(1L);
            device.setExternalId("esp-temp-1");
            when(deviceRepository.findByExternalId("esp-temp-1")).thenReturn(Optional.of(device));
            when(telemetryMapper.toEntityList(any(), any())).thenReturn(List.of());
            when(deviceRepository.findHomeIdByExternalId("esp-temp-1")).thenReturn(Optional.of(10L));

            TelemetryRequest request = new TelemetryRequest("esp-temp-1", 45.0, 40.0, 200.0);
            telemetryService.saveTelemetry(request);

            verify(mockPush, times(1)).sendNotificationToHome(
                    eq(10L),
                    eq("Alerta Crítico: Temperatura Alta"),
                    contains("45.0"),
                    argThat(map -> "CRITICAL_TEMPERATURE".equals(map.get("eventType")) && "CRITICAL".equals(map.get("urgency")))
            );
        }

        @Test
        @DisplayName("Limite Térmico Abaixo de Crítico: 44.9 °C NÃO dispara push notification")
        void thermalBoundary_44Point9Degrees_DoesNotDispatchPush() {
            PushNotificationService mockPush = mock(PushNotificationService.class);
            TelemetryService telemetryService = new TelemetryService(
                    telemetryRepository, deviceRepository, telemetryMapper, notificationService, mockPush
            );

            Device device = new Device();
            device.setId(1L);
            device.setExternalId("esp-temp-1");
            when(deviceRepository.findByExternalId("esp-temp-1")).thenReturn(Optional.of(device));
            when(telemetryMapper.toEntityList(any(), any())).thenReturn(List.of());

            TelemetryRequest request = new TelemetryRequest("esp-temp-1", 44.9, 40.0, 200.0);
            telemetryService.saveTelemetry(request);

            verifyNoInteractions(mockPush);
        }

        @Test
        @DisplayName("Limite Térmico de Congelamento: 0.0 °C dispara push notification FREEZE_WARNING")
        void thermalBoundary_0Degrees_DispatchesFreezePush() {
            PushNotificationService mockPush = mock(PushNotificationService.class);
            TelemetryService telemetryService = new TelemetryService(
                    telemetryRepository, deviceRepository, telemetryMapper, notificationService, mockPush
            );

            Device device = new Device();
            device.setId(1L);
            device.setExternalId("esp-temp-1");
            when(deviceRepository.findByExternalId("esp-temp-1")).thenReturn(Optional.of(device));
            when(telemetryMapper.toEntityList(any(), any())).thenReturn(List.of());
            when(deviceRepository.findHomeIdByExternalId("esp-temp-1")).thenReturn(Optional.of(10L));

            TelemetryRequest request = new TelemetryRequest("esp-temp-1", 0.0, 80.0, 50.0);
            telemetryService.saveTelemetry(request);

            verify(mockPush, times(1)).sendNotificationToHome(
                    eq(10L),
                    eq("Alerta: Risco de Congelamento"),
                    contains("0.0"),
                    argThat(map -> "FREEZE_WARNING".equals(map.get("eventType")) && "WARNING".equals(map.get("urgency")))
            );
        }

        @Test
        @DisplayName("Limite Térmico Acima de Congelamento: 0.1 °C NÃO dispara push notification")
        void thermalBoundary_0Point1Degrees_DoesNotDispatchPush() {
            PushNotificationService mockPush = mock(PushNotificationService.class);
            TelemetryService telemetryService = new TelemetryService(
                    telemetryRepository, deviceRepository, telemetryMapper, notificationService, mockPush
            );

            Device device = new Device();
            device.setId(1L);
            device.setExternalId("esp-temp-1");
            when(deviceRepository.findByExternalId("esp-temp-1")).thenReturn(Optional.of(device));
            when(telemetryMapper.toEntityList(any(), any())).thenReturn(List.of());

            TelemetryRequest request = new TelemetryRequest("esp-temp-1", 0.1, 80.0, 50.0);
            telemetryService.saveTelemetry(request);

            verifyNoInteractions(mockPush);
        }

        @Test
        @DisplayName("Transição de Dispositivo ONLINE -> OFFLINE: Dispara push notification para a residência")
        void deviceOffline_OnlineToOffline_DispatchesPush() {
            PushNotificationService mockPush = mock(PushNotificationService.class);
            DeviceService deviceService = new DeviceService(
                    deviceRepository, homeMemberRepository, deviceMapper, notificationService, mockPush
            );

            Home home = new Home();
            home.setId(55L);

            Device device = new Device();
            device.setId(1L);
            device.setExternalId("esp32-node");
            device.setName("Sensor Jardim");
            device.setHome(home);
            device.setStatus(DeviceStatus.ONLINE);

            when(deviceRepository.findByExternalId("esp32-node")).thenReturn(Optional.of(device));
            when(deviceRepository.save(any(Device.class))).thenReturn(device);

            deviceService.updateDeviceStatus("esp32-node", DeviceStatus.OFFLINE);

            assertEquals(DeviceStatus.OFFLINE, device.getStatus());
            verify(mockPush, times(1)).sendNotificationToHome(
                    eq(55L),
                    eq("Dispositivo Desconectado"),
                    contains("Sensor Jardim"),
                    argThat(map -> "DEVICE_OFFLINE".equals(map.get("eventType")) && "esp32-node".equals(map.get("deviceId")))
            );
        }

        @Test
        @DisplayName("Transição de Dispositivo OFFLINE -> OFFLINE (Idempotência): Não dispara push duplicado")
        void deviceOffline_OfflineToOffline_DoesNotDispatchDuplicatePush() {
            PushNotificationService mockPush = mock(PushNotificationService.class);
            DeviceService deviceService = new DeviceService(
                    deviceRepository, homeMemberRepository, deviceMapper, notificationService, mockPush
            );

            Home home = new Home();
            home.setId(55L);

            Device device = new Device();
            device.setId(1L);
            device.setExternalId("esp32-node");
            device.setName("Sensor Jardim");
            device.setHome(home);
            device.setStatus(DeviceStatus.OFFLINE);

            when(deviceRepository.findByExternalId("esp32-node")).thenReturn(Optional.of(device));
            when(deviceRepository.save(any(Device.class))).thenReturn(device);

            deviceService.updateDeviceStatus("esp32-node", DeviceStatus.OFFLINE);

            verifyNoInteractions(mockPush);
        }

        @Test
        @DisplayName("checkStaleDevices: Detecta nó ONLINE inativo e dispara push de desconexão")
        void checkStaleDevices_StaleDeviceFound_TransitionsToOfflineAndDispatchesPush() {
            PushNotificationService mockPush = mock(PushNotificationService.class);
            DeviceService deviceService = new DeviceService(
                    deviceRepository, homeMemberRepository, deviceMapper, notificationService, mockPush
            );

            Home home = new Home();
            home.setId(66L);

            Device staleDevice = new Device();
            staleDevice.setId(2L);
            staleDevice.setExternalId("esp32-stale-node");
            staleDevice.setName("Lâmpada Corredor");
            staleDevice.setHome(home);
            staleDevice.setStatus(DeviceStatus.ONLINE);
            staleDevice.setLastSeenAt(LocalDateTime.now().minusMinutes(5));

            when(deviceRepository.findByStatusAndLastSeenAtBefore(eq(DeviceStatus.ONLINE), any(LocalDateTime.class)))
                    .thenReturn(List.of(staleDevice));

            deviceService.checkStaleDevices();

            assertEquals(DeviceStatus.OFFLINE, staleDevice.getStatus());
            verify(mockPush, times(1)).sendNotificationToHome(
                    eq(66L),
                    eq("Dispositivo Desconectado"),
                    contains("Lâmpada Corredor"),
                    argThat(map -> "DEVICE_OFFLINE".equals(map.get("eventType")) && "esp32-stale-node".equals(map.get("deviceId")))
            );
        }
    }
}
