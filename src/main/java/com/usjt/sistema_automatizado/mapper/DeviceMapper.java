package com.usjt.sistema_automatizado.mapper;

import com.usjt.sistema_automatizado.dto.request.DeviceRequest;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.Home;
import org.springframework.stereotype.Component;

@Component
public class DeviceMapper {

    public Device toEntity(DeviceRequest request, Home home) {
        if (request == null) {
            return null;
        }

        Device device = new Device();
        device.setHome(home);
        // Lendo os dados do record (sem o 'get')
        device.setExternalId(request.externalId());
        device.setName(request.name());
        device.setRoom(request.room());

        return device;
    }

    public DeviceResponse toResponse(Device entity) {
        if (entity == null) {
            return null;
        }

        // Criando a resposta diretamente pelo construtor do record
        return new DeviceResponse(
                entity.getId(),
                entity.getExternalId(),
                entity.getName(),
                entity.getRoom(),
                entity.getStatus(),
                entity.getLastSeenAt(),
                entity.getFirmwareVersion()
        );
    }
}