package com.usjt.sistema_automatizado.mapper;

import com.usjt.sistema_automatizado.dto.request.EventRequest;
import com.usjt.sistema_automatizado.dto.response.EventResponse;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.Event;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public Event toEntity(EventRequest request, Device device) {
        if (request == null || device == null) {
            return null;
        }

        Event event = new Event();
        event.setDevice(device);
        event.setType(request.type());
        event.setOccurredAt(request.ts());

        return event;
    }

    public EventResponse toResponse(Event entity) {
        if (entity == null) {
            return null;
        }

        // Se ninguém confirmou o alerta, o nome fica a null
        String acknowledgedByName = null;
        if (entity.getAcknowledgedBy() != null) {
            acknowledgedByName = entity.getAcknowledgedBy().getName();
        }

        return new EventResponse(
                entity.getId(),
                entity.getDevice().getExternalId(),
                entity.getDevice().getName(),
                entity.getDevice().getRoom(),
                entity.getType(),
                entity.getOccurredAt(),
                entity.getReceivedAt(),
                entity.getAcknowledgedAt(),
                acknowledgedByName
        );
    }
}