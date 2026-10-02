package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.CommandRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.CommandResponse;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.mapper.DeviceActionMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.Home;
import com.usjt.sistema_automatizado.model.entity.HomeMember;
import com.usjt.sistema_automatizado.model.enums.CommandDeliveryStatus;
import com.usjt.sistema_automatizado.model.enums.CommandType;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeviceActionServicteTest {

    @InjectMocks
    private DeviceActionService deviceActionService;

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private HomeMemberRepository homeMemberRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private DeviceActionMapper commandMapper;
    @Mock
    private MqttService mqttService;
    @Mock
    private CommandAckService commandAckService;
    @Mock
    private DeviceService deviceService;

    @Test
    void processHeartbeat_DeveDelegarParaDeviceService() {
        // Arrange
        TelemetryRequest request = new TelemetryRequest(
                1,
                "esp32-dht11-01",
                LocalDateTime.now(),
                25.5,
                60.0,
                450.0
        );
        DeviceResponse expectedResponse = mock(DeviceResponse.class);
        when(deviceService.processHeartbeat(request)).thenReturn(expectedResponse);

        // Act
        DeviceResponse actualResponse = deviceActionService.processHeartbeat(request);

        // Assert
        assertSame(expectedResponse, actualResponse);
        verify(deviceService, times(1)).processHeartbeat(request);
    }

    @Test
    void sendCommand_DeveLancarExcecao_QuandoDispositivoNaoEncontrado() {
        // Arrange
        Long deviceId = 99L;
        Long requesterId = 1L;
        CommandRequest request = new CommandRequest(CommandType.TURN_ON);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> deviceActionService.sendCommand(deviceId, request, requesterId)
        );

        assertEquals("Dispositivo não encontrado.", exception.getMessage());
        verify(mqttService, never()).sendCommand(anyString(), anyString());
    }

    @Test
    void sendCommand_DeveLancarExcecao_QuandoDispositivoEstiverOffline() {
        // Arrange
        Long deviceId = 1L;
        Long requesterId = 1L;
        CommandRequest request = new CommandRequest(CommandType.TURN_ON);

        Device device = new Device();
        device.setId(deviceId);
        device.setName("Lâmpada Sala");
        device.setStatus(DeviceStatus.OFFLINE);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> deviceActionService.sendCommand(deviceId, request, requesterId)
        );

        assertTrue(exception.getMessage().contains("OFFLINE"));
        verify(mqttService, never()).sendCommand(anyString(), anyString());
    }

    @Test
    void sendCommand_DeveLancarExcecao_QuandoUsuarioNaoPertenceACasa() {
        // Arrange
        Long deviceId = 1L;
        Long requesterId = 99L;
        CommandRequest request = new CommandRequest(CommandType.TURN_ON);

        Home home = new Home();
        home.setId(10L);

        Device device = new Device();
        device.setId(deviceId);
        device.setName("Lâmpada");
        device.setStatus(DeviceStatus.ONLINE);
        device.setHome(home);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(homeMemberRepository.findByHomeIdAndUserId(10L, requesterId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> deviceActionService.sendCommand(deviceId, request, requesterId)
        );

        assertEquals("Não tem acesso aos dispositivos desta casa.", exception.getMessage());
        verify(mqttService, never()).sendCommand(anyString(), anyString());
    }

    @Test
    void sendCommand_DeveLancarExcecao_QuandoUsuarioSolicitanteNaoEncontrado() {
        // Arrange
        Long deviceId = 1L;
        Long requesterId = 2L;
        CommandRequest request = new CommandRequest(CommandType.TURN_ON);

        Home home = new Home();
        home.setId(10L);

        Device device = new Device();
        device.setId(deviceId);
        device.setName("Lâmpada");
        device.setStatus(DeviceStatus.ONLINE);
        device.setHome(home);

        HomeMember member = new HomeMember();

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(homeMemberRepository.findByHomeIdAndUserId(10L, requesterId)).thenReturn(Optional.of(member));
        when(appUserRepository.findById(requesterId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> deviceActionService.sendCommand(deviceId, request, requesterId)
        );

        assertEquals("Utilizador não encontrado.", exception.getMessage());
        verify(mqttService, never()).sendCommand(anyString(), anyString());
    }

    @Test
    void sendCommand_DevePublicarMqttEAguardarAckComSucesso() {
        // Arrange
        Long deviceId = 1L;
        Long requesterId = 2L;
        CommandRequest request = new CommandRequest(CommandType.TURN_ON);

        Home home = new Home();
        home.setId(10L);

        Device device = new Device();
        device.setId(deviceId);
        device.setExternalId("esp32-sala-01");
        device.setName("Lâmpada Sala");
        device.setStatus(DeviceStatus.ONLINE);
        device.setHome(home);

        HomeMember member = new HomeMember();

        AppUser requester = new AppUser();
        requester.setId(requesterId);
        requester.setName("Alice");

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(homeMemberRepository.findByHomeIdAndUserId(10L, requesterId)).thenReturn(Optional.of(member));
        when(appUserRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(commandMapper.toCommandJson(eq(request), anyString())).thenReturn("{\"action\":\"FAN_ON\"}");
        when(commandAckService.aguardarAck(anyString())).thenReturn(CommandDeliveryStatus.DELIVERED);

        // Act
        CommandResponse response = deviceActionService.sendCommand(deviceId, request, requesterId);

        // Assert
        assertNotNull(response);
        assertEquals("esp32-sala-01", response.deviceExternalId());
        assertEquals(CommandType.TURN_ON, response.type());
        assertEquals(CommandDeliveryStatus.DELIVERED, response.status());
        assertEquals("Alice", response.requestedBy());
        assertNotNull(response.dispatchedAt());

        verify(mqttService, times(1)).sendCommand(eq("esp32-sala-01"), eq("{\"action\":\"FAN_ON\"}"));
        verify(commandAckService, times(1)).aguardarAck(anyString());
    }
}
