package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.AutomationConfigRequest;
import com.usjt.sistema_automatizado.dto.response.AutomationConfigResponse;
import com.usjt.sistema_automatizado.config.mqtt.MqttGateway;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço responsável pela gestão de parâmetros de automação residencial e regras de histerese.
 *
 * <p>Protege a integridade física dos atuadores (relé do ventilador) garantindo que os limiares de acionamento
 * configurem uma faixa operacional coerente e que apenas administradores da residência alterem parâmetros.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationConfigService {

    private final AutomationConfigRepository automationConfigRepository;
    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final AppUserRepository appUserRepository;
    private final AutomationConfigMapper automationConfigMapper;
    private final MqttGateway mqttGateway;

    /**
     * Recupera as configurações ativas de automação de um dispositivo.
     * Permitido a todos os membros associados à residência (ADMIN e FAMILY).
     *
     * @param deviceId identificador interno do dispositivo
     * @param requesterId identificador do usuário solicitante
     * @return DTO com os limiares de histerese e padrões de iluminação
     * @throws EntityNotFoundException se o dispositivo ou as configurações não existirem
     * @throws IllegalArgumentException se o solicitante não pertencer à residência
     */
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

    /**
     * Atualiza os limiares operacionais de um dispositivo no banco de dados.
     *
     * <p><b>Validação de Histerese:</b> Exige estritamente {@code fanOnAbove > fanOffBelow} para proteger o
     * relé e motor contra ciclagem rápida (comutação intermitente em variações de frações de grau).</p>
     *
     * <p><b>Controle de Acesso:</b> Operação restrita ao papel {@link HomeRole#ADMIN}.</p>
     *
     * @param deviceId identificador interno do dispositivo
     * @param request novos limiares e parâmetros de automação
     * @param requesterId identificador do usuário solicitante
     * @return DTO com a configuração salva e atualizada
     * @throws IllegalArgumentException se o limiar de ligar for menor ou igual ao de desligar, ou se o usuário não for ADMIN
     * @throws EntityNotFoundException se o dispositivo ou usuário solicitante não existirem
     */
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

        // Publica a nova configuração no tópico retained do firmware.
        // O ESP32 recebe imediatamente se online; se offline, receberá na reconexão (retained).
        String configTopic = "devices/" + device.getExternalId() + "/config";
        String configPayload = automationConfigMapper.toMqttConfigPayload(savedConfig);
        mqttGateway.sendToMqtt(configTopic, configPayload);
        log.info("[Config] Configuracao publicada no topico MQTT {}: {}", configTopic, configPayload);

        return automationConfigMapper.toResponse(savedConfig);
    }
}