package com.usjt.sistema_automatizado.mapper;

import com.usjt.sistema_automatizado.dto.request.AutomationConfigRequest;
import com.usjt.sistema_automatizado.dto.response.AutomationConfigResponse;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.AutomationConfig;
import com.usjt.sistema_automatizado.model.entity.Device;
import org.springframework.stereotype.Component;

@Component
public class AutomationConfigMapper {

    // Cria uma nova configuração ou atualiza uma existente
    public AutomationConfig toEntity(AutomationConfigRequest request, Device device, AppUser updatedBy, AutomationConfig existingConfig) {
        AutomationConfig config = existingConfig != null ? existingConfig : new AutomationConfig();

        config.setDevice(device);
        config.setFanOnAbove(request.fanOnAbove());
        config.setFanOffBelow(request.fanOffBelow());
        config.setDarkBelow(request.darkBelow());
        config.setDoorbellPattern(request.doorbellPattern());
        config.setBellR(request.bellR());
        config.setBellG(request.bellG());
        config.setBellB(request.bellB());
        config.setUpdatedBy(updatedBy);

        return config;
    }

    public AutomationConfigResponse toResponse(AutomationConfig entity) {
        if (entity == null) {
            return null;
        }

        return new AutomationConfigResponse(
                entity.getId(),
                entity.getDevice().getId(),
                entity.getFanOnAbove(),
                entity.getFanOffBelow(),
                entity.getDarkBelow(),
                entity.getDoorbellPattern(),
                entity.getBellR(),
                entity.getBellG(),
                entity.getBellB(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy().getName()
        );
    }

    /**
     * Serializa a configuração no formato JSON que o firmware ESP32 espera no tópico
     * {@code devices/{deviceId}/config} (retained, QoS 1).
     *
     * @param config entidade salva
     * @return JSON string para publicação MQTT
     */
    public String toMqttConfigPayload(AutomationConfig config) {
        return String.format(
                "{\"v\":1,\"fanOnAbove\":%.1f,\"fanOffBelow\":%.1f,\"bellR\":%d,\"bellG\":%d,\"bellB\":%d}",
                config.getFanOnAbove(),
                config.getFanOffBelow(),
                config.getBellR(),
                config.getBellG(),
                config.getBellB()
        );
    }
}