package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.DeviceRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.mapper.DeviceMapper;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.HomeMember;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.model.enums.HomeRole;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final DeviceMapper deviceMapper;

    @Transactional
    public DeviceResponse createDevice(Long homeId, DeviceRequest request, Long requesterId) {
        // 1. Verifica se o solicitante pertence à casa
        HomeMember member = homeMemberRepository.findByHomeIdAndUserId(homeId, requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso a esta casa."));

        // 2. Autorização: Apenas o ADMIN pode adicionar dispositivos
        if (member.getRole() != HomeRole.ADMIN) {
            throw new IllegalArgumentException("Apenas o ADMIN pode registar novos dispositivos.");
        }

        // 3. Verifica se já existe um dispositivo com o mesmo externalId
        if (deviceRepository.findByExternalId(request.externalId()).isPresent()) {
            throw new IllegalArgumentException("Já existe um dispositivo registado com este identificador externo.");
        }

        // 4. Cria o dispositivo (passando a casa que já recuperámos da tabela de membros)
        Device device = deviceMapper.toEntity(request, member.getHome());
        device.setStatus(DeviceStatus.OFFLINE); // Nasce offline até enviar a primeira telemetria/evento

        Device savedDevice = deviceRepository.save(device);

        return deviceMapper.toResponse(savedDevice);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> listDevices(Long homeId, Long requesterId) {
        // 1. Verifica se tem acesso (ADMIN ou FAMILY podem ver a lista)
        homeMemberRepository.findByHomeIdAndUserId(homeId, requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso a esta casa."));

        // 2. Devolve a lista mapeada
        return deviceRepository.findByHomeId(homeId).stream()
                .map(deviceMapper::toResponse)
                .toList();
    }

    @Transactional
    public void updateDeviceStatus(String externalId, DeviceStatus status) {
        Device device = deviceRepository.findByExternalId(externalId)
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo não encontrado: " + externalId));
        device.setStatus(status);
        device.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
        deviceRepository.save(device);
    }

    @Transactional
    public DeviceResponse processHeartbeat(TelemetryRequest request) {
        Device device = deviceRepository.findByExternalId(request.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("Dispositivo não encontrado com o identificador externo fornecido."));

        device.setStatus(DeviceStatus.ONLINE);
        device.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
        Device updatedDevice = deviceRepository.save(device);

        return deviceMapper.toResponse(updatedDevice);
    }
}