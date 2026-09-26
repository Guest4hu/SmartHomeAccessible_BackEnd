package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.config.mqtt.MqttGateway;
import com.usjt.sistema_automatizado.dto.request.CommandRequest;
import com.usjt.sistema_automatizado.dto.request.DeviceRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.CommandResponse;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.mapper.DeviceMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.HomeMember;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.model.enums.HomeRole;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
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

    private final AppUserRepository  appUserRepository;
    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final DeviceMapper deviceMapper;
    private final com.usjt.sistema_automatizado.config.mqtt.MqttGateway  mqttGateway;

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
    @Transactional
    public CommandResponse sendCommand(Long deviceId, CommandRequest request, Long requesterId) {
        // 1. Busca o dispositivo
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo não encontrado."));

        // 2. Proteção de Hardware: Bloqueia comandos se o ESP32 estiver offline
        if (device.getStatus() == com.usjt.sistema_automatizado.model.enums.DeviceStatus.OFFLINE) {
            throw new IllegalArgumentException(
                    "Não é possível enviar o comando. O dispositivo '" + device.getName() + "' está OFFLINE."
            );
        }

        // 3. Segurança: Garante que o utilizador pertence à casa (ADMIN ou FAMILY podem ligar luzes/ventilação)
        homeMemberRepository.findByHomeIdAndUserId(device.getHome().getId(), requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso aos dispositivos desta casa."));

        AppUser requester = appUserRepository.findById(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("Utilizador não encontrado."));

        // 4. Integração IoT (Espaço reservado para as Etapas 12 a 15)
        // ======= DISPARO PARA O HARDWARE VIA MQTT =======

        // 1. O tópico exato que o ESP32 escuta
        String topic = "devices/" + device.getExternalId() + "/cmd";

        // 2. Transforma o enum TURN_ON/TURN_OFF no padrão que o C++ espera (FAN_ON/FAN_OFF)
        String actionValue = request.type().name().equals("TURN_ON") ? "FAN_ON" : "FAN_OFF";
        String jsonPayload = "{ \"action\": \"" + actionValue + "\" }";

        // 3. Envia para a nuvem
        mqttGateway.sendToMqtt(topic, jsonPayload);

        // ==================================================

        return new CommandResponse(
                device.getExternalId(),
                request.type(),
                "DISPATCHED", // <--- Mantemos o status como despachado
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC),
                requester.getName()
        );
    }
}