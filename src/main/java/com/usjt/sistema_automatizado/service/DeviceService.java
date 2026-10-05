package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.DeviceRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.mapper.DeviceMapper;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.HomeMember;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.model.enums.HomeRole;
import com.usjt.sistema_automatizado.model.enums.SensoryChannel;
import com.usjt.sistema_automatizado.model.enums.UrgencyLevel;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Serviço responsável pelo cadastro, inventário e gestão de conectividade dos dispositivos IoT da residência.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final DeviceMapper deviceMapper;
    private final NotificationService notificationService;
    private final PushNotificationService pushNotificationService;

    /**
     * Registra um novo dispositivo IoT vinculando o externalId de hardware à residência especificada.
     *
     * <p><b>Controle de Acesso:</b> Exclusivo para usuários com o papel {@link HomeRole#ADMIN}.
     * O dispositivo inicia com status {@code OFFLINE} até que sua primeira mensagem seja processada.</p>
     *
     * @param homeId identificador da residência
     * @param request dados do dispositivo (externalId, nome e cômodo)
     * @param requesterId identificador do usuário solicitante
     * @return DTO com os dados do dispositivo cadastrado
     * @throws IllegalArgumentException se o solicitante não for ADMIN ou se o externalId já estiver em uso
     */
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

    /**
     * Lista os dispositivos pertencentes a uma residência para membros autorizados (ADMIN ou FAMILY).
     *
     * @param homeId identificador da casa
     * @param requesterId identificador do usuário autenticado
     * @return lista com os dispositivos da casa
     * @throws IllegalArgumentException se o solicitante não pertencer à residência
     */
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

    /**
     * Atualiza o estado de conectividade e o carimbo de última atividade (lastSeenAt) do dispositivo.
     * Invocado principalmente pelo processamento de mensagens LWT via MQTT.
     *
     * @param externalId identificador de hardware do ESP32
     * @param status novo estado (ONLINE ou OFFLINE)
     * @throws EntityNotFoundException se o dispositivo não existir
     */
    @Transactional
    public void updateDeviceStatus(String externalId, DeviceStatus status) {
        Device device = deviceRepository.findByExternalId(externalId)
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo não encontrado: " + externalId));

        DeviceStatus previousStatus = device.getStatus();
        device.setStatus(status);
        device.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
        deviceRepository.save(device);

        // Se o dispositivo transitou para OFFLINE, dispara push notification para a residência
        if (status == DeviceStatus.OFFLINE && previousStatus != DeviceStatus.OFFLINE) {
            Long homeId = (device.getHome() != null) ? device.getHome().getId() : null;
            if (homeId == null) {
                homeId = deviceRepository.findHomeIdByExternalId(externalId).orElse(null);
            }

            if (homeId != null) {
                String title = "Dispositivo Desconectado";
                String body = "O dispositivo " + device.getName() + " parou de se comunicar.";
                Map<String, String> data = Map.of(
                        "eventType", "DEVICE_OFFLINE",
                        "deviceId", externalId,
                        "deviceName", device.getName(),
                        "urgency", "WARNING"
                );
                pushNotificationService.sendNotificationToHome(homeId, title, body, data);
            }
        }
    }

    /**
     * Processa o sinal de atividade (heartbeat) de um dispositivo comutando seu status para ONLINE,
     * validando que o solicitante pertence à residência vinculada.
     *
     * @param request dados do heartbeat contendo o externalId
     * @param requesterId identificador do usuário solicitante
     * @return DTO com o dispositivo atualizado
     * @throws IllegalArgumentException se o dispositivo não for encontrado ou se o usuário não pertencer à residência
     */
    @Transactional
    public DeviceResponse processHeartbeat(TelemetryRequest request, Long requesterId) {
        Device device = deviceRepository.findByExternalId(request.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("Dispositivo não encontrado com o identificador externo fornecido."));

        if (requesterId != null && device.getHome() != null) {
            homeMemberRepository.findByHomeIdAndUserId(device.getHome().getId(), requesterId)
                    .orElseThrow(() -> new IllegalArgumentException("Não tem acesso a esta casa."));
        }

        device.setStatus(DeviceStatus.ONLINE);
        device.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
        Device updatedDevice = deviceRepository.save(device);

        return deviceMapper.toResponse(updatedDevice);
    }

    /**
     * Sobrecarga para processamento de heartbeat sem validação de usuário (compatibilidade).
     */
    @Transactional
    public DeviceResponse processHeartbeat(TelemetryRequest request) {
        return processHeartbeat(request, null);
    }

    /**
     * Monitora periodicamente nós IoT marcados como ONLINE cuja última atividade (lastSeenAt)
     * é anterior a 3 minutos. Comuta para OFFLINE e notifica os moradores via SSE.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkStaleDevices() {
        LocalDateTime threshold = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(3);
        List<Device> staleDevices = deviceRepository.findByStatusAndLastSeenAtBefore(DeviceStatus.ONLINE, threshold);

        for (Device device : staleDevices) {
            log.warn("[Heartbeat Monitor] Dispositivo {} ({}) sem comunicacao recente (lastSeenAt={}). Marcando como OFFLINE.",
                    device.getName(), device.getExternalId(), device.getLastSeenAt());
            device.setStatus(DeviceStatus.OFFLINE);
            deviceRepository.save(device);

            notificationService.dispatchEvent(
                    "device-status",
                    device.getExternalId(),
                    "DEVICE_OFFLINE",
                    UrgencyLevel.WARNING,
                    SensoryChannel.MULTIMODAL,
                    "Dispositivo " + device.getName() + " sem sinal de comunicacao.",
                    "Atenção: o dispositivo " + device.getName() + " parou de se comunicar."
            );

            // Dispara Push Notification para a residência do nó stale
            Long homeId = (device.getHome() != null) ? device.getHome().getId() : null;
            if (homeId == null) {
                homeId = deviceRepository.findHomeIdByExternalId(device.getExternalId()).orElse(null);
            }

            if (homeId != null) {
                String title = "Dispositivo Desconectado";
                String body = "O dispositivo " + device.getName() + " parou de se comunicar.";
                Map<String, String> data = Map.of(
                        "eventType", "DEVICE_OFFLINE",
                        "deviceId", device.getExternalId(),
                        "deviceName", device.getName(),
                        "urgency", "WARNING"
                );
                pushNotificationService.sendNotificationToHome(homeId, title, body, data);
            }
        }
    }
}