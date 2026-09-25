package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.AutomationConfigRequest;
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
}