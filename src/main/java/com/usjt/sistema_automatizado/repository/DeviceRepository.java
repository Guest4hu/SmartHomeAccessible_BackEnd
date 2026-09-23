package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByExternalId(String externalId);
}