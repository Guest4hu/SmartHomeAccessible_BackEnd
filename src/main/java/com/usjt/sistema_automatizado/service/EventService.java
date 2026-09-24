package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.EventRequest;
import com.usjt.sistema_automatizado.dto.response.EventResponse;
import com.usjt.sistema_automatizado.mapper.EventMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.Event;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.EventRepository;
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
public class EventService {

    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final AppUserRepository appUserRepository;
    private final EventMapper eventMapper;

    // 1. Simula a receção do toque da campainha do ESP32
    @Transactional
    public EventResponse createEvent(EventRequest request) {
        Device device = deviceRepository.findByExternalId(request.deviceId())
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo não encontrado."));

        Event event = eventMapper.toEntity(request, device);
        Event savedEvent = eventRepository.save(event);

        // NOTA: Na Etapa 15, é exatamente aqui que vamos disparar o aviso via WebSocket/FCM
        // para os telemóveis de todos os membros da casa.

        return eventMapper.toResponse(savedEvent);
    }

    // 2. O painel web consulta os alertas (com opção de ver só os pendentes)
    @Transactional(readOnly = true)
    public List<EventResponse> getHomeEvents(Long homeId, Long requesterId, boolean pendingOnly) {
        homeMemberRepository.findByHomeIdAndUserId(homeId, requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso aos eventos desta casa."));

        List<Event> events;
        if (pendingOnly) {
            events = eventRepository.findByDeviceHomeIdAndAcknowledgedAtIsNullOrderByOccurredAtDesc(homeId);
        } else {
            events = eventRepository.findByDeviceHomeIdOrderByOccurredAtDesc(homeId);
        }

        return events.stream().map(eventMapper::toResponse).toList();
    }

    // 3. Acessibilidade: Um morador avisa o sistema que já viu a notificação
    @Transactional
    public EventResponse acknowledgeEvent(Long eventId, Long requesterId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento não encontrado."));

        // Garante que a pessoa pertence à casa de onde veio o evento
        Long homeId = event.getDevice().getHome().getId();
        homeMemberRepository.findByHomeIdAndUserId(homeId, requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso a esta casa."));

        // Impede que se confirme o mesmo evento duas vezes
        if (event.getAcknowledgedAt() != null) {
            throw new IllegalArgumentException("Este evento já foi confirmado por " + event.getAcknowledgedBy().getName());
        }

        AppUser user = appUserRepository.findById(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("Utilizador não encontrado."));

        // Regista quem viu e a que horas
        event.setAcknowledgedAt(LocalDateTime.now(ZoneOffset.UTC));
        event.setAcknowledgedBy(user);

        return eventMapper.toResponse(eventRepository.save(event));
    }
}