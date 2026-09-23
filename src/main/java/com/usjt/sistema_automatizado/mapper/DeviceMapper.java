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
        device.setExternalId(request.getExternalId());
        device.setName(request.getName());
        device.setRoom(request.getRoom());

        // Os campos status, lastSeenAt e firmwareVersion assumem os valores
        // por omissão da entidade ou ficam nulos no momento do registo inicial.
        return device;
    }

    public DeviceResponse toResponse(Device entity) {
        if (entity == null) {
            return null;
        }

        DeviceResponse response = new DeviceResponse();
        response.setId(entity.getId());
        response.setExternalId(entity.getExternalId());
        response.setName(entity.getName());
        response.setRoom(entity.getRoom());
        response.setStatus(entity.getStatus());
        response.setLastSeenAt(entity.getLastSeenAt());
        response.setFirmwareVersion(entity.getFirmwareVersion());

        return response;
    }
}