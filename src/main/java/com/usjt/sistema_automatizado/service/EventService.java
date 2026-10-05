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

/**
 * Serviço responsável pelo ciclo de vida de eventos, disparos de sensores e confirmações de alertas acessíveis.
 *
 * <p>Centraliza a persistência de acionamentos de campainha e o mecanismo de confirmação de leitura
 * que previne duplicidade de atendimento por moradores da mesma residência.</p>
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final AppUserRepository appUserRepository;
    private final EventMapper eventMapper;

    /**
     * Registra um novo evento emitido pelo hardware (campainha, detecção de presença ou confirmação).
     *
     * @param request dados do evento e identificador de hardware do dispositivo emissor
     * @return DTO com o evento persistido
     * @throws EntityNotFoundException se o dispositivo não for localizado pelo externalId
     */
    @Transactional
    public EventResponse createEvent(EventRequest request) {
        Device device = deviceRepository.findByExternalId(request.deviceId())
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo não encontrado."));

        Event event = eventMapper.toEntity(request, device);
        Event savedEvent = eventRepository.save(event);

        return eventMapper.toResponse(savedEvent);
    }

    /**
     * Consulta o histórico de eventos de uma residência com opção de filtrar apenas alertas não atendidos.
     *
     * @param homeId identificador da residência
     * @param requesterId identificador do usuário solicitante
     * @param pendingOnly se verdadeiro, retorna apenas eventos sem confirmação de leitura
     * @return lista de eventos ordenada do mais recente para o mais antigo
     * @throws IllegalArgumentException se o solicitante não pertencer à residência
     */
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

    /**
     * Registra a confirmação de que um morador visualizou o alerta acessível disparado pela campainha ou sensor.
     *
     * @param eventId identificador do evento
     * @param requesterId identificador do morador que confirmou o atendimento
     * @return evento atualizado com a data/hora em UTC e a identificação do morador
     * @throws EntityNotFoundException se o evento ou usuário não existirem
     * @throws IllegalArgumentException se o morador não pertencer à residência ou o alerta já estiver confirmado
     */
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