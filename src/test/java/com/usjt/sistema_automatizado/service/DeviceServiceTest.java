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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private HomeMemberRepository homeMemberRepository;

    @Mock
    private DeviceMapper deviceMapper;

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
    void processHeartbeat_DeveAtualizarStatusParaOnline_QuandoDispositivoExistir() {
        // Arrange
        String deviceId = "esp32-dht11-01";
        TelemetryRequest request = new TelemetryRequest(
                1,                      // v (versão)
                deviceId,               // deviceId
                LocalDateTime.now(),    // ts (timestamp)
                25.5,                   // temperature
                60.0,                   // humidity
                450.0                   // luminosity
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
        assertEquals(DeviceStatus.ONLINE, device.getStatus()); // Garante que passou para ONLINE
        verify(deviceRepository, times(1)).save(device);
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
}