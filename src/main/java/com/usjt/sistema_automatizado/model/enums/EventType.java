package com.usjt.sistema_automatizado.model.enums;

public enum EventType
{
    DOORBELL,
    PRESENCE_DETECTED,
    HIGH_TEMPERATURE,
    DEVICE_ONLINE,
    DEVICE_OFFLINE,
    UNKNOWN_EVENT;

    // Método utilitário para converter a String que vem do JSON de forma segura
    public static EventType fromString(String type) {
        if (type == null || type.isBlank()) {
            return UNKNOWN_EVENT;
        }
        try {
            // Converte para maiúsculo para garantir que "doorbell" ou "DOORBELL" funcionem
            return EventType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Se o ESP32 enviar um texto que não existe no Enum (ex: "FOGO"),
            // não quebra a aplicação, apenas retorna o desconhecido.
            return UNKNOWN_EVENT;
        }
    }
}
