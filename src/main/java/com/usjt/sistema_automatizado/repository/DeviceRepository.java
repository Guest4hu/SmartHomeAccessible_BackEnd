package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    // Usado pela Telemetria e Eventos para encontrar o dispositivo pelo ID que o ESP32 envia
    Optional<Device> findByExternalId(String externalId);

    // Usado para listar todos os dispositivos de uma casa específica
    List<Device> findByHomeId(Long homeId);
}