package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.DeviceRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.mapper.DeviceMapper;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.Home;
import com.usjt.sistema_automatizado.model.entity.HomeMember;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.model.enums.HomeRole;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private HomeMemberRepository homeMemberRepository;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void createDevice_DeveLancarExcecao_QuandoUsuarioNaoPertenceACasa() {
        // Arrange
        Long homeId = 1L;
        Long requesterId = 99L; // Utilizador que não pertence à casa
        DeviceRequest request = mock(DeviceRequest.class); // Mockamos o request para evitar problemas de construtor

        when(homeMemberRepository.findByHomeIdAndUserId(homeId, requesterId))
                .thenReturn(Optional.empty()); // Simula que não encontrou o vínculo

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> deviceService.createDevice(homeId, request, requesterId)
        );

        assertEquals("Não tem acesso a esta casa.", exception.getMessage());
        verify(deviceRepository, never()).save(any()); // Garante que NUNCA tentou guardar no banco
    }

    @Test
    void createDevice_DeveLancarExcecao_QuandoUsuarioNaoForAdmin() {
        // Arrange
        Long homeId = 1L;
        Long requesterId = 2L;
        DeviceRequest request = mock(DeviceRequest.class);

        HomeMember member = new HomeMember();
        member.setRole(HomeRole.FAMILY); // Permissão insuficiente!

        when(homeMemberRepository.findByHomeIdAndUserId(homeId, requesterId))
                .thenReturn(Optional.of(member));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> deviceService.createDevice(homeId, request, requesterId)
        );

        assertEquals("Apenas o ADMIN pode registar novos dispositivos.", exception.getMessage());
    }

    @Test
    void createDevice_DeveSalvarDispositivoOffline_QuandoTudoEstiverCorreto() {
        // Arrange
        Long homeId = 1L;
        Long requesterId = 1L;

        // Mock do request
        DeviceRequest request = mock(DeviceRequest.class);
        when(request.externalId()).thenReturn("esp32-lampada-01");

        // Configurar a casa e o membro (ADMIN)
        Home home = new Home();
        home.setId(homeId);

        HomeMember member = new HomeMember();
        member.setHome(home);
        member.setRole(HomeRole.ADMIN); // Permissão de Admin!

        Device deviceEntity = new Device();
        deviceEntity.setId(10L);

        DeviceResponse responseDto = mock(DeviceResponse.class);

        // Ensinando os mocks
        when(homeMemberRepository.findByHomeIdAndUserId(homeId, requesterId)).thenReturn(Optional.of(member));
        when(deviceRepository.findByExternalId("esp32-lampada-01")).thenReturn(Optional.empty()); // ExternalId livre
        when(deviceMapper.toEntity(request, home)).thenReturn(deviceEntity);
        when(deviceRepository.save(any(Device.class))).thenReturn(deviceEntity);
        when(deviceMapper.toResponse(deviceEntity)).thenReturn(responseDto);

        // Act
        DeviceResponse result = deviceService.createDevice(homeId, request, requesterId);

        // Assert
        assertNotNull(result);
        assertEquals(DeviceStatus.OFFLINE, deviceEntity.getStatus()); // Verifica se o service forçou para OFFLINE
        verify(deviceRepository, times(1)).save(deviceEntity); // Garante que gravou na base de dados
    }

    @Test
    void listDevices_DeveRetornarLista_QuandoUsuarioPertenceACasa() {
        // Arrange
        Long homeId = 1L;
        Long requesterId = 1L;

        HomeMember member = new HomeMember(); // Se o membro existe, a listagem é permitida

        Device device1 = new Device();
        Device device2 = new Device();
        List<Device> deviceList = List.of(device1, device2);

        DeviceResponse response1 = mock(DeviceResponse.class);
        DeviceResponse response2 = mock(DeviceResponse.class);

        when(homeMemberRepository.findByHomeIdAndUserId(homeId, requesterId)).thenReturn(Optional.of(member));
        when(deviceRepository.findByHomeId(homeId)).thenReturn(deviceList);
        when(deviceMapper.toResponse(device1)).thenReturn(response1);
        when(deviceMapper.toResponse(device2)).thenReturn(response2);

        // Act
        List<DeviceResponse> result = deviceService.listDevices(homeId, requesterId);

        // Assert
        assertEquals(2, result.size());
        verify(deviceRepository, times(1)).findByHomeId(homeId); // Validou se pesquisou no banco pelo ID da casa
    }

    @Test
    void updateDeviceStatus_DeveAtualizarStatusELastSeenAt_QuandoDispositivoExistir() {
        // Arrange
        String externalId = "esp32-sala-01";
        Device device = new Device();
        device.setId(1L);
        device.setExternalId(externalId);
        device.setStatus(DeviceStatus.OFFLINE);

        when(deviceRepository.findByExternalId(externalId)).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);

        // Act
        deviceService.updateDeviceStatus(externalId, DeviceStatus.ONLINE);

        // Assert
        assertEquals(DeviceStatus.ONLINE, device.getStatus());
        assertNotNull(device.getLastSeenAt());
        verify(deviceRepository, times(1)).save(device);
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void updateDeviceStatus_DeveDispararPushNotification_QuandoTransitarParaOffline() {
        String externalId = "esp32-sensor-01";
        Home home = new Home();
        home.setId(10L);

        Device device = new Device();
        device.setId(1L);
        device.setExternalId(externalId);
        device.setName("Sensor Portão");
        device.setHome(home);
        device.setStatus(DeviceStatus.ONLINE);

        when(deviceRepository.findByExternalId(externalId)).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);

        deviceService.updateDeviceStatus(externalId, DeviceStatus.OFFLINE);

        assertEquals(DeviceStatus.OFFLINE, device.getStatus());
        verify(pushNotificationService, times(1)).sendNotificationToHome(
                eq(10L),
                eq("Dispositivo Desconectado"),
                contains("Sensor Portão"),
                anyMap()
        );
    }

    @Test
    void updateDeviceStatus_NaoDeveDispararPushNotification_QuandoJaEstavaOffline() {
        String externalId = "esp32-sensor-01";
        Home home = new Home();
        home.setId(10L);

        Device device = new Device();
        device.setId(1L);
        device.setExternalId(externalId);
        device.setName("Sensor Portão");
        device.setHome(home);
        device.setStatus(DeviceStatus.OFFLINE);

        when(deviceRepository.findByExternalId(externalId)).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);

        deviceService.updateDeviceStatus(externalId, DeviceStatus.OFFLINE);

        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void updateDeviceStatus_DeveLancarExcecao_QuandoDispositivoNaoExistir() {
        // Arrange
        String externalId = "esp32-inexistente";
        when(deviceRepository.findByExternalId(externalId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class,
                () -> deviceService.updateDeviceStatus(externalId, DeviceStatus.ONLINE));
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void processHeartbeat_DeveAtualizarStatusParaOnlineELastSeenAt_QuandoDispositivoExistir() {
        // Arrange
        String deviceId = "esp32-dht11-01";
        TelemetryRequest request = new TelemetryRequest(
                1,
                deviceId,
                LocalDateTime.now(),
                25.5,
                60.0,
                450.0
        );

        Device device = new Device();
        device.setId(10L);
        device.setExternalId(deviceId);
        device.setStatus(DeviceStatus.OFFLINE);

        DeviceResponse expectedResponse = mock(DeviceResponse.class);

        when(deviceRepository.findByExternalId(deviceId)).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);
        when(deviceMapper.toResponse(device)).thenReturn(expectedResponse);

        // Act
        DeviceResponse actualResponse = deviceService.processHeartbeat(request);

        // Assert
        assertNotNull(actualResponse);
        assertEquals(DeviceStatus.ONLINE, device.getStatus());
        assertNotNull(device.getLastSeenAt());
        verify(deviceRepository, times(1)).save(device);
        verify(deviceMapper, times(1)).toResponse(device);
    }

    @Test
    void processHeartbeat_DeveLancarExcecao_QuandoDispositivoNaoEncontrado() {
        // Arrange
        String deviceId = "esp32-inexistente";
        TelemetryRequest request = new TelemetryRequest(
                1,
                deviceId,
                LocalDateTime.now(),
                null, null, null
        );

        when(deviceRepository.findByExternalId(deviceId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> deviceService.processHeartbeat(request)
        );

        assertEquals("Dispositivo não encontrado com o identificador externo fornecido.", exception.getMessage());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void processHeartbeat_ComRequesterId_DeveLancarExcecao_QuandoNaoPertencerACasa() {
        // Arrange
        String deviceId = "esp32-sala-01";
        Long requesterId = 99L;
        TelemetryRequest request = new TelemetryRequest(
                1,
                deviceId,
                LocalDateTime.now(),
                null, null, null
        );

        Home home = new Home();
        home.setId(10L);

        Device device = new Device();
        device.setId(1L);
        device.setExternalId(deviceId);
        device.setHome(home);

        when(deviceRepository.findByExternalId(deviceId)).thenReturn(Optional.of(device));
        when(homeMemberRepository.findByHomeIdAndUserId(10L, requesterId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> deviceService.processHeartbeat(request, requesterId)
        );

        assertEquals("Não tem acesso a esta casa.", exception.getMessage());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void processHeartbeat_ComRequesterId_DeveAtualizarStatus_QuandoPertencerACasa() {
        // Arrange
        String deviceId = "esp32-sala-01";
        Long requesterId = 2L;
        TelemetryRequest request = new TelemetryRequest(
                1,
                deviceId,
                LocalDateTime.now(),
                null, null, null
        );

        Home home = new Home();
        home.setId(10L);

        Device device = new Device();
        device.setId(1L);
        device.setExternalId(deviceId);
        device.setHome(home);
        device.setStatus(DeviceStatus.OFFLINE);

        HomeMember member = new HomeMember();
        DeviceResponse expectedResponse = mock(DeviceResponse.class);

        when(deviceRepository.findByExternalId(deviceId)).thenReturn(Optional.of(device));
        when(homeMemberRepository.findByHomeIdAndUserId(10L, requesterId)).thenReturn(Optional.of(member));
        when(deviceRepository.save(device)).thenReturn(device);
        when(deviceMapper.toResponse(device)).thenReturn(expectedResponse);

        // Act
        DeviceResponse actualResponse = deviceService.processHeartbeat(request, requesterId);

        // Assert
        assertEquals(expectedResponse, actualResponse);
        assertEquals(DeviceStatus.ONLINE, device.getStatus());
        verify(deviceRepository, times(1)).save(device);
    }

    @Test
    void checkStaleDevices_DeveMarcarOfflineENotificar_QuandoDispositivosEstiveremStale() {
        Device staleDevice = new Device();
        staleDevice.setId(1L);
        staleDevice.setExternalId("esp32-stale");
        staleDevice.setName("Sensor Sala");
        staleDevice.setStatus(DeviceStatus.ONLINE);
        staleDevice.setLastSeenAt(LocalDateTime.now().minusMinutes(5));

        when(deviceRepository.findByStatusAndLastSeenAtBefore(eq(DeviceStatus.ONLINE), any(LocalDateTime.class)))
                .thenReturn(List.of(staleDevice));

        deviceService.checkStaleDevices();

        assertEquals(DeviceStatus.OFFLINE, staleDevice.getStatus());
        verify(deviceRepository, times(1)).save(staleDevice);
        verify(notificationService, times(1)).dispatchEvent(
                eq("device-status"),
                eq("esp32-stale"),
                eq("DEVICE_OFFLINE"),
                any(),
                any(),
                anyString(),
                anyString()
        );
    }

    @Test
    void checkStaleDevices_DeveMarcarOfflineEDispararPushParaResidencia_QuandoDispositivoComCasaStale() {
        Home home = new Home();
        home.setId(10L);

        Device staleDevice = new Device();
        staleDevice.setId(1L);
        staleDevice.setExternalId("esp32-stale-home");
        staleDevice.setName("Sensor Garagem");
        staleDevice.setHome(home);
        staleDevice.setStatus(DeviceStatus.ONLINE);
        staleDevice.setLastSeenAt(LocalDateTime.now().minusMinutes(5));

        when(deviceRepository.findByStatusAndLastSeenAtBefore(eq(DeviceStatus.ONLINE), any(LocalDateTime.class)))
                .thenReturn(List.of(staleDevice));

        deviceService.checkStaleDevices();

        assertEquals(DeviceStatus.OFFLINE, staleDevice.getStatus());
        verify(pushNotificationService, times(1)).sendNotificationToHome(
                eq(10L),
                eq("Dispositivo Desconectado"),
                contains("Sensor Garagem"),
                anyMap()
        );
    }

    @Test
    void checkStaleDevices_NaoDeveFazerNada_QuandoNaoHouverDispositivosStale() {
        when(deviceRepository.findByStatusAndLastSeenAtBefore(eq(DeviceStatus.ONLINE), any(LocalDateTime.class)))
                .thenReturn(List.of());

        deviceService.checkStaleDevices();

        verify(deviceRepository, never()).save(any());
        verifyNoInteractions(notificationService);
        verifyNoInteractions(pushNotificationService);
    }
}