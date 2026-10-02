package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    // Usado pela Telemetria e Eventos para encontrar o dispositivo pelo ID que o ESP32 envia
    Optional<Device> findByExternalId(String externalId);

    // Usado para listar todos os dispositivos de uma casa específica
    List<Device> findByHomeId(Long homeId);

    // Usado para identificar diretamente a casa vinculada ao dispositivo sem sobrecarga de JOIN
    @Query("SELECT d.home.id FROM Device d WHERE d.externalId = :externalId")
    Optional<Long> findHomeIdByExternalId(@Param("externalId") String externalId);
}