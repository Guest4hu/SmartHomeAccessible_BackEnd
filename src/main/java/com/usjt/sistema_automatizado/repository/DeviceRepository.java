package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de persistência e consulta de dispositivos IoT (ESP32).
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    /**
     * Localiza a entidade pelo identificador de hardware único embutido de fábrica (MAC efuse).
     *
     * @param externalId identificador de hardware (ex: "esp32-100100C40A24")
     * @return Optional contendo o dispositivo se encontrado
     */
    Optional<Device> findByExternalId(String externalId);

    /**
     * Lista todos os dispositivos registrados no escopo de uma residência.
     *
     * @param homeId identificador da casa
     * @return lista de dispositivos pertencentes à residência
     */
    List<Device> findByHomeId(Long homeId);

    /**
     * Projeção otimizada para identificar a residência vinculada ao hardware sem carregar a entidade completa.
     * Utilizado para resolução rápida de tenancy em notificações SSE.
     *
     * @param externalId identificador de hardware enviado no tópico MQTT
     * @return Optional com o ID da casa vinculada
     */
    @Query("SELECT d.home.id FROM Device d WHERE d.externalId = :externalId")
    Optional<Long> findHomeIdByExternalId(@Param("externalId") String externalId);
}