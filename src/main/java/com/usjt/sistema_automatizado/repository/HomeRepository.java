package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.Home;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HomeRepository extends JpaRepository<Home, Long> {
}