package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.HomeMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de verificação de vínculos e controle de autorização de membros de residências.
 */
@Repository
public interface HomeMemberRepository extends JpaRepository<HomeMember, Long> {

    /**
     * Localiza o vínculo de um usuário com uma residência para validação de acesso e extração do papel (ADMIN/FAMILY).
     *
     * @param homeId identificador da casa
     * @param userId identificador do usuário autenticado
     * @return Optional com o vínculo se o usuário for membro da casa
     */
    Optional<HomeMember> findByHomeIdAndUserId(Long homeId, Long userId);

    /**
     * Lista todas as associações residenciais de um determinado usuário.
     *
     * @param userId identificador do usuário
     * @return lista de vínculos com residências
     */
    List<HomeMember> findByUserId(Long userId);

    /**
     * Lista todos os membros pertencentes a uma residência específica.
     *
     * @param homeId identificador da casa
     * @return lista de membros da casa
     */
    List<HomeMember> findByHomeId(Long homeId);

    /**
     * Retorna diretamente os IDs dos usuários vinculados a uma casa para transmissão direcionada de notificações SSE.
     *
     * @param homeId identificador da casa
     * @return lista de identificadores dos usuários moradores
     */
    @Query("SELECT hm.user.id FROM HomeMember hm WHERE hm.home.id = :homeId")
    List<Long> findUserIdsByHomeId(@Param("homeId") Long homeId);
}