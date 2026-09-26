package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.CommandRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.CommandResponse;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.mapper.DeviceActionMapper;
import com.usjt.sistema_automatizado.mapper.DeviceMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;


@Service
@RequiredArgsConstructor
public class DeviceActionService {

    private final DeviceMapper deviceMapper;
    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final AppUserRepository appUserRepository;
    private final DeviceActionMapper commandMapper;
    private final MqttService mqttService;

    @Transactional
    public CommandResponse sendCommand(Long deviceId, CommandRequest request, Long requesterId) {
        // 1. Busca o dispositivo
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo não encontrado."));

        // 2. Proteção de Hardware: Bloqueia comandos se o ESP32 estiver offline
        if (device.getStatus() == DeviceStatus.OFFLINE) {
            throw new IllegalArgumentException(
                    "Não é possível enviar o comando. O dispositivo '" + device.getName() + "' está OFFLINE, Ative o No Aplicativo."
            );
        }

        // 3. Segurança: Garante que o utilizador pertence à casa
        homeMemberRepository.findByHomeIdAndUserId(device.getHome().getId(), requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso aos dispositivos desta casa."));

        AppUser requester = appUserRepository.findById(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("Utilizador não encontrado."));

        // 4. Converte o request para JSON usando o Mapper
        String jsonPayload = commandMapper.toCommandJson(request);

        // 5. Dispara para o hardware via MQTT
        mqttService.sendCommand(device.getExternalId(), jsonPayload);

        // 6. Retorna a resposta padronizada
        return new CommandResponse(
                device.getExternalId(),
                request.type(),
                "DISPATCHED",
                LocalDateTime.now(ZoneOffset.UTC),
                requester.getName()
        );
    }
    @Transactional
    public DeviceResponse processHeartbeat(TelemetryRequest request) {
        // 1. Procura o dispositivo pelo deviceId enviado no payload de telemetria
        Device device = deviceRepository.findByExternalId(request.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("Dispositivo não encontrado com o identificador externo fornecido."));

        // 2. Atualiza o estado para ONLINE
        device.setStatus(DeviceStatus.ONLINE);

        // 3. Persiste a alteração na base de dados
        Device updatedDevice = deviceRepository.save(device);

        // 4. Retorna a resposta DTO
        return deviceMapper.toResponse(updatedDevice);
    }
}