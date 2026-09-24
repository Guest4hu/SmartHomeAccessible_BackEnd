package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // Consulta por nome de método que o Spring Data JPA escreve automaticamente o SQL
    Optional<AppUser> findByEmail(String email);
}