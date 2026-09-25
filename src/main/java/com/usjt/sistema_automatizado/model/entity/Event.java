package com.usjt.sistema_automatizado.model.entity;

import com.usjt.sistema_automatizado.model.enums.EventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "event",
        indexes = {
                @Index(name = "idx_event_device_time", columnList = "device_id, occurred_at"),
                @Index(name = "idx_event_type_time", columnList = "type, occurred_at")
        }
)
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
    @Column(nullable = false, length = 30) // Ajustado rigorosamente para VARCHAR(30)
    private EventType type;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private AppUser acknowledgedBy;

    @PrePersist
    protected void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}