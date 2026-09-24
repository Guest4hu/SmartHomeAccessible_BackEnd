package com.usjt.sistema_automatizado.model.entity;

import com.usjt.sistema_automatizado.model.enums.EventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "event")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EventType type;

    // Hora exata em que o botão foi pressionado no hardware
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // Hora em que o nosso servidor Spring Boot recebeu o aviso
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    // Quando é que o utilizador surdo (ou familiar) viu o alerta
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    // Quem foi o utilizador exato que viu o alerta
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private AppUser acknowledgedBy;

    @PrePersist
    public void prePersist() {
        this.receivedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}