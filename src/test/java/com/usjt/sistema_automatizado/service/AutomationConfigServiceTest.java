package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.AutomationConfigRequest;
import com.usjt.sistema_automatizado.mapper.AutomationConfigMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.Home;
import com.usjt.sistema_automatizado.model.entity.HomeMember;
import com.usjt.sistema_automatizado.model.enums.HomeRole;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.AutomationConfigRepository;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationConfigServiceTest {


    @Mock
    private AutomationConfigRepository configRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private HomeMemberRepository homeMemberRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AutomationConfigMapper automationConfigMapper;

    @Mock
    private com.usjt.sistema_automatizado.config.mqtt.MqttGateway mqttGateway;

    @InjectMocks
    private AutomationConfigService automationConfigService;

    @Test
    void updateConfig_DeveLancarExcecao_QuandoHistereseInvalida() {
        // Arrange
        Long deviceId = 1L;
        Long requesterId = 2L;

        // Simular um utilizador a tentar ligar a ventoinha a 25 graus, mas a desligar a 26 graus (Cenário que estragaria o relé)
        AutomationConfigRequest request = new AutomationConfigRequest(25.0, 26.0, 100.0, "LONG_SHORT");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> automationConfigService.updateConfig(deviceId, request, requesterId)
        );

        assertEquals("O limite para ligar a ventoinha (25.0°C) deve ser maior que o limite para desligar (26.0°C).", exception.getMessage());

        // Garante que nem sequer foi à base de dados procurar o dispositivo
        verify(deviceRepository, never()).findById(any());
    }

    @Test
    void updateConfig_DeveLancarExcecao_QuandoUsuarioForSomenteFamily() {
        // Arrange
        Long deviceId = 1L;
        Long requesterId = 2L;
        AutomationConfigRequest request = new AutomationConfigRequest(28.0, 24.0, 100.0, "LONG_SHORT"); // Histerese correta

        Home home = new Home();
        home.setId(10L);

        Device device = new Device();
        device.setId(deviceId);
        device.setHome(home);

        HomeMember member = new HomeMember();
        member.setRole(HomeRole.FAMILY); // Permissão insuficiente

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(homeMemberRepository.findByHomeIdAndUserId(10L, requesterId)).thenReturn(Optional.of(member));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> automationConfigService.updateConfig(deviceId, request, requesterId)
        );

        assertEquals("Apenas o ADMIN da casa pode alterar as configurações de automação.", exception.getMessage());
        verify(configRepository, never()).save(any());
    }

    @Test
    void updateConfig_DeveSalvarEPublicarMqttComRetainedTrueEQos1() {
        // Arrange
        Long deviceId = 1L;
        Long requesterId = 2L;
        AutomationConfigRequest request = new AutomationConfigRequest(28.0, 24.0, 100.0, "LONG_SHORT", 255, 0, 0);

        Home home = new Home();
        home.setId(10L);

        Device device = new Device();
        device.setId(deviceId);
        device.setExternalId("esp32-sala-01");
        device.setHome(home);

        HomeMember member = new HomeMember();
        member.setRole(HomeRole.ADMIN);

        AppUser requester = new AppUser();
        requester.setId(requesterId);

        com.usjt.sistema_automatizado.model.entity.AutomationConfig configEntity =
                new com.usjt.sistema_automatizado.model.entity.AutomationConfig();
        com.usjt.sistema_automatizado.dto.response.AutomationConfigResponse expectedResponse =
                mock(com.usjt.sistema_automatizado.dto.response.AutomationConfigResponse.class);

        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(homeMemberRepository.findByHomeIdAndUserId(10L, requesterId)).thenReturn(Optional.of(member));
        when(appUserRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(configRepository.findByDeviceId(deviceId)).thenReturn(Optional.empty());
        when(automationConfigMapper.toEntity(eq(request), eq(device), eq(requester), isNull())).thenReturn(configEntity);
        when(configRepository.save(configEntity)).thenReturn(configEntity);
        when(automationConfigMapper.toMqttConfigPayload(configEntity)).thenReturn("{\"fanOnAbove\":28.0}");
        when(automationConfigMapper.toResponse(configEntity)).thenReturn(expectedResponse);

        // Act
        var actualResponse = automationConfigService.updateConfig(deviceId, request, requesterId);

        // Assert
        assertEquals(expectedResponse, actualResponse);
        verify(configRepository, times(1)).save(configEntity);
        verify(mqttGateway, times(1)).sendToMqtt("devices/esp32-sala-01/config", 1, true, "{\"fanOnAbove\":28.0}");
    }
}