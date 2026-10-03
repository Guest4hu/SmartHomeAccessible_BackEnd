package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório de persistência e consulta de ocorrências de eventos e alertas residenciais.
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Lista o histórico completo de eventos de uma residência ordenado do mais recente para o mais antigo.
     *
     * @param homeId identificador da residência
     * @return lista de eventos ordenados descendentemente por data de ocorrência
     */
    List<Event> findByDeviceHomeIdOrderByOccurredAtDesc(Long homeId);

    /**
     * Recupera exclusivamente os alertas que ainda não tiveram sua visualização confirmada pelos moradores.
     * Elemento central para a experiência acessível no painel da casa.
     *
     * @param homeId identificador da residência
     * @return lista de eventos não atendidos ordenados pelo instante de disparo
     */
    List<Event> findByDeviceHomeIdAndAcknowledgedAtIsNullOrderByOccurredAtDesc(Long homeId);
}