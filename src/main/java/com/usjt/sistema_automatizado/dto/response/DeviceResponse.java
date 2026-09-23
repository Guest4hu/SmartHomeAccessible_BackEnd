package com.usjt.sistema_automatizado.dto.response;

import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
public class DeviceResponse {

    private Long id;
    private String externalId;
    private String name;
    private String room;
    private DeviceStatus status;
    private LocalDateTime lastSeenAt;
    private String firmwareVersion;

}