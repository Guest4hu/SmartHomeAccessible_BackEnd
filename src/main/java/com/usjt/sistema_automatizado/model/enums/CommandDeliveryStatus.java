package com.usjt.sistema_automatizado.model.enums;

/**
 * Status tipado de entrega de comando enviado pelo backend ao hardware (ESP32).
 */
public enum CommandDeliveryStatus {
    DELIVERED,
    FAILED,
    TIMEOUT;

    /**
     * Converte com segurança uma String em CommandDeliveryStatus,
     * retornando FAILED caso o valor seja nulo, vazio ou desconhecido.
     */
    public static CommandDeliveryStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            return FAILED;
        }
        try {
            return CommandDeliveryStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FAILED;
        }
    }
}
