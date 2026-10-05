package com.usjt.sistema_automatizado.model.entity;

import com.usjt.sistema_automatizado.model.enums.PushPlatform;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Entidade representativa de um token de dispositivo para notificações push (FCM).
 *
 * <p>Mapeia a tabela {@code push_token}, vinculando credenciais de registro móvel
 * ou web a um morador ({@link AppUser}).</p>
 */
@Entity
@Table(name = "push_token",
        uniqueConstraints = {@UniqueConstraint(name = "uk_push_token", columnNames = {"token"})},
        indexes = {@Index(name = "idx_push_token_user", columnList = "user_id")}
)
@Getter
@Setter
@NoArgsConstructor
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 512, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PushPlatform platform;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public PushToken(AppUser user, String token, PushPlatform platform) {
        this.user = user;
        this.token = token;
        this.platform = platform;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public void updateUsage() {
        this.lastUsedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
