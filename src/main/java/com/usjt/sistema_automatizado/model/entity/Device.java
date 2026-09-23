package com.usjt.sistema_automatizado.model.entity;

import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "device")
@Getter
@Setter
@NoArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_id")
    private Home home;


    @Size(max = 50)
    @NotNull
    @Column(unique = true, name = "external_id", length = 50, nullable = false)
    private String externalId;

    @Size(max = 100)
    @NotNull
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Size(max = 50)
    @Column(name = "room", length = 50)
    private String room;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private DeviceStatus status = DeviceStatus.OFFLINE;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Size(max = 20)
    @Column(name = "firmware_version", length = 20)
    private String firmwareVersion;

    @Column(name = "created_at",nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist(){
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }



}
