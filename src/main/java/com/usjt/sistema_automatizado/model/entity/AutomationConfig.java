package com.usjt.sistema_automatizado.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Entidade que encapsula os limiares operacionais e parâmetros de automação de um dispositivo.
 *
 * <p>Define a faixa de histerese de temperatura ({@code fanOnAbove} e {@code fanOffBelow}) para
 * o acionamento do relé de ventilação no IoT Edge, evitando o desgaste prematuro por comutações
 * repetidas em torno do mesmo valor. As alterações são restritas ao papel {@code ADMIN}.</p>
 */
@Entity
@Table(name = "automation_config")
@Getter
@Setter
@NoArgsConstructor
public class AutomationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relacionamento 1 para 1: Cada dispositivo tem exatamente 1 configuração
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private Device device;

    @Column(name = "fan_on_above", nullable = false)
    private Double fanOnAbove;

    @Column(name = "fan_off_below", nullable = false)
    private Double fanOffBelow;

    @Column(name = "dark_below", nullable = false)
    private Double darkBelow;

    @Column(name = "doorbell_pattern", length = 50)
    private String doorbellPattern;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", nullable = false)
    private AppUser updatedBy;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}