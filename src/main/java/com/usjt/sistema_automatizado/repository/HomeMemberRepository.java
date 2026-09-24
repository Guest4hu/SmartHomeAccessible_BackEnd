package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.HomeMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HomeMemberRepository extends JpaRepository<HomeMember, Long> {

    // Essencial para Autorização: Verifica se o utilizador pertence à casa e qual o seu papel
    Optional<HomeMember> findByHomeIdAndUserId(Long homeId, Long userId);

    // Para o Dashboard: Lista todas as casas às quais o utilizador tem acesso
    List<HomeMember> findByUserId(Long userId);

    // Para gestão pelo ADMIN: Lista todos os membros de uma determinada casa
    List<HomeMember> findByHomeId(Long homeId);
}