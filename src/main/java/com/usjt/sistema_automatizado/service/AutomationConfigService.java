package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.AutomationConfigRequest;
import com.usjt.sistema_automatizado.dto.response.AutomationConfigResponse;
import com.usjt.sistema_automatizado.mapper.AutomationConfigMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.AutomationConfig;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.HomeMember;
import com.usjt.sistema_automatizado.model.enums.HomeRole;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.AutomationConfigRepository;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutomationConfigService {

    private final AutomationConfigRepository automationConfigRepository;
    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final AppUserRepository appUserRepository;
    private final AutomationConfigMapper automationConfigMapper;

    @Transactional(readOnly = true)
    public AutomationConfigResponse getConfig(Long deviceId, Long requesterId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo não encontrado."));

        // Qualquer membro da casa (ADMIN ou FAMILY) pode ler a configuração
        homeMemberRepository.findByHomeIdAndUserId(device.getHome().getId(), requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso aos dispositivos desta casa."));

        AutomationConfig config = automationConfigRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Configuração de automação ainda não definida para este dispositivo."));

        return automationConfigMapper.toResponse(config);
    }

    @Transactional
    public AutomationConfigResponse updateConfig(Long deviceId, AutomationConfigRequest request, Long requesterId) {
        // 1. Validação da Histerese (Proteção do Hardware)
        if (request.fanOnAbove() <= request.fanOffBelow()) {
            throw new IllegalArgumentException("O limite para ligar a ventoinha (" + request.fanOnAbove() +
                    "°C) deve ser maior que o limite para desligar (" + request.fanOffBelow() + "°C).");
        }

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo não encontrado."));

        // 2. Validação de Autorização
        HomeMember member = homeMemberRepository.findByHomeIdAndUserId(device.getHome().getId(), requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso a esta casa."));

        if (member.getRole() != HomeRole.ADMIN) {
            throw new IllegalArgumentException("Apenas o ADMIN da casa pode alterar as configurações de automação.");
        }

        AppUser requester = appUserRepository.findById(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("Utilizador não encontrado."));

        // 3. Atualiza se existir, ou cria se for a primeira vez
        AutomationConfig existingConfig = automationConfigRepository.findByDeviceId(deviceId).orElse(null);
        AutomationConfig configToSave = automationConfigMapper.toEntity(request, device, requester, existingConfig);

        AutomationConfig savedConfig = automationConfigRepository.save(configToSave);

        // NOTA: Na Etapa 12, é aqui que enviaremos a nova configuração para o tópico MQTT do ESP32 (Retained message)

        return automationConfigMapper.toResponse(savedConfig);
    }
}