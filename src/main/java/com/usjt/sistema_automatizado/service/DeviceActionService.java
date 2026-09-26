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
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class DeviceActionService {

    private final DeviceMapper deviceMapper;
    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final AppUserRepository appUserRepository;
    private final DeviceActionMapper commandMapper;
    private final MqttService mqttService;
    private final CommandAckService commandAckService;

    // Não usar @Transactional aqui: o método bloqueia aguardando o ACK do firmware
    // por até 5 segundos. Manter uma transação JPA aberta durante esse período
    // seguraria a conexão do pool desnecessariamente.
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

        // 4. Gera um UUID único para esta requisição (correlationId).
        //    O firmware ecoa este ID na confirmação, permitindo ao backend
        //    identificar exatamente qual Future deve ser resolvido,
        //    sem interferência de eventos físicos (DOORBELL, PRESENCE, etc.).
        String correlationId = UUID.randomUUID().toString();

        // 5. Converte o request para JSON com o correlationId embutido
        String jsonPayload = commandMapper.toCommandJson(request, correlationId);

        // 6. Publica o comando no MQTT
        mqttService.sendCommand(device.getExternalId(), jsonPayload);

        // 7. Aguarda a confirmação do firmware pelo correlationId específico (até 5s)
        String ackStatus = commandAckService.aguardarAck(correlationId);

        // 8. Retorna a resposta com o status real de entrega ao dispositivo
        return new CommandResponse(
                device.getExternalId(),
                request.type(),
                ackStatus,
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