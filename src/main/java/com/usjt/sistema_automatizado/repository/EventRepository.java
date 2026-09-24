package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // Lista todos os eventos de uma casa (histórico) do mais recente para o mais antigo
    List<Event> findByDeviceHomeIdOrderByOccurredAtDesc(Long homeId);

    // Essencial para a Acessibilidade: Lista apenas os alertas pendentes (não vistos) de uma casa
    List<Event> findByDeviceHomeIdAndAcknowledgedAtIsNullOrderByOccurredAtDesc(Long homeId);
}