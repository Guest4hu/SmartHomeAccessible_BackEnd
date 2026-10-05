package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repositório de persistência e consulta de tokens push (FCM) de moradores.
 */
@Repository
public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    /**
     * Localiza um registro de push token pelo valor do token único.
     * Utilizado para verificação de existência e upsert idempotente.
     */
    Optional<PushToken> findByToken(String token);

    /**
     * Localiza um push token pertencente a um usuário específico.
     * Utilizado para verificação estrita de ownership antes da revogação.
     */
    Optional<PushToken> findByTokenAndUserId(String token, Long userId);

    /**
     * Remove um token associado a um usuário de forma direta.
     */
    void deleteByTokenAndUserId(String token, Long userId);

    /**
     * Lista todos os tokens cadastrados para um determinado usuário.
     */
    List<PushToken> findByUserId(Long userId);

    /**
     * Busca todos os push tokens pertencentes aos moradores vinculados à residência especificada.
     */
    @Query("SELECT pt FROM PushToken pt WHERE pt.user.id IN (SELECT hm.user.id FROM HomeMember hm WHERE hm.home.id = :homeId)")
    List<PushToken> findAllByHomeId(@Param("homeId") Long homeId);

    /**
     * Busca push tokens para um conjunto de identificadores de usuários.
     */
    @Query("SELECT pt FROM PushToken pt WHERE pt.user.id IN :userIds")
    List<PushToken> findByUserIdIn(@Param("userIds") Collection<Long> userIds);

    /**
     * Remove um token obsoleto ou inválido pelo valor string do token (ex: desregistrado pelo FCM).
     */
    void deleteByToken(String token);
}
