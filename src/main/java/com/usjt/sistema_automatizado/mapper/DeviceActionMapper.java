package com.usjt.sistema_automatizado.mapper;

import com.usjt.sistema_automatizado.dto.request.CommandRequest;
import org.springframework.stereotype.Component;

@Component
public class DeviceActionMapper {
    public String toCommandJson(CommandRequest request) {
        // Traduz o enum do Spring para o comando esperado pelo firmware C++
        String actionValue = request.type().name().equals("TURN_ON") ? "FAN_ON" : "FAN_OFF";
        return "{ \"action\": \"" + actionValue + "\" }";
    }
}
