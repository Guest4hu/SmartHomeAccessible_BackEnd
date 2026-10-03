package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.AutomationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositório de persistência para as configurações de automação e histerese por dispositivo.
 */
@Repository
public interface AutomationConfigRepository extends JpaRepository<AutomationConfig, Long> {

    // Procura a configuração baseando-se no ID interno do dispositivo
    Optional<AutomationConfig> findByDeviceId(Long deviceId);
}