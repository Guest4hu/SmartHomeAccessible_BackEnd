package com.usjt.sistema_automatizado.model.entity;

import com.usjt.sistema_automatizado.model.enums.HomeRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Entidade associativa que vincula um usuário a uma residência específica atribuindo-lhe um papel.
 *
 * <p>Permite que um mesmo usuário pertença a múltiplos lares com papéis distintos (ex: {@code ADMIN}
 * na própria residência e {@code FAMILY} na casa de familiares). Assegura a unicidade do par
 * {@code (home_id, user_id)}.</p>
 */
@Entity
@Table(name = "home_member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_home_user", columnNames = {"home_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class HomeMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_id", nullable = false)
    private Home home;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HomeRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    public void prePersist() {
        this.joinedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}