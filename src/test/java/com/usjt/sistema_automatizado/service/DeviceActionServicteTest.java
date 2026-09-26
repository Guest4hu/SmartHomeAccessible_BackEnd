package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.mapper.DeviceMapper;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeviceActionServicteTest {

    @Mock
    private DeviceActionService deviceActionService;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private HomeMemberRepository homeMemberRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private DeviceMapper deviceMapper;

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
                () -> deviceActionService.processHeartbeat(request)
        );

        assertEquals("Dispositivo não encontrado com o identificador externo fornecido.", exception.getMessage());
        verify(deviceRepository, never()).save(any());
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
        DeviceResponse actualResponse = deviceActionService.processHeartbeat(request);

        // Assert
        assertNotNull(actualResponse);
        assertEquals(DeviceStatus.ONLINE, device.getStatus()); // Garante que passou para ONLINE
        verify(deviceRepository, times(1)).save(device);
    }
}
