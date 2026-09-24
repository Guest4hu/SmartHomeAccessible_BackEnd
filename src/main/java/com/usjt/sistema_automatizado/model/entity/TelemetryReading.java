package com.usjt.sistema_automatizado.model.entity;

import com.usjt.sistema_automatizado.model.enums.MetricType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "telemetry_reading", uniqueConstraints = {
        @UniqueConstraint(name = "uk_telemetry_device_metric_time", columnNames = {"device_id", "metric", "recorded_at"})
})
@Getter
@Setter
@NoArgsConstructor
public class TelemetryReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MetricType metric;

    @NotNull
    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @NotNull
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    public void prePersist() {
        this.receivedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}