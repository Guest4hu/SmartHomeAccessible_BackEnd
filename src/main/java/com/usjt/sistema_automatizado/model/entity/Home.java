package com.usjt.sistema_automatizado.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Entidade raiz que delimita a residência e o escopo de segurança multi-tenant do sistema.
 *
 * <p>Todos os dispositivos, automações, membros familiares e eventos pertencem a uma residência,
 * garantindo o isolamento total de dados e regras de negócio entre famílias.</p>
 */
@Entity
@Table(name = "home")
@Getter
@Setter
@NoArgsConstructor
public class Home {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 100)
    @NotNull
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at",nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Roda automaticamente antes do primeiro INSERT. Grava a data em UTC.
    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}