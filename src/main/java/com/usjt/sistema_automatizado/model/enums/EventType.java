package com.usjt.sistema_automatizado.model.enums;

/**
 * Catálogo de tipos de eventos registrados pelo sistema.
 *
 * <p>Abrange disparos físicos no edge (campainha e presença), transições de conectividade
 * (LWT de status) e confirmações de entrega bidirecional de comandos/arquivos pelo firmware.</p>
 */
public enum EventType
{
    DOORBELL,
    PRESENCE_DETECTED,
    HIGH_TEMPERATURE,
    DEVICE_ONLINE,
    DEVICE_OFFLINE,
    // Confirmações bidirecionais de comando enviado pelo backend ao ESP32
    COMMAND_SUCCESS,
    COMMAND_FAILED,
    // Confirmações de recebimento de arquivo/firmware enviado ao ESP32
    FILE_RECEIVED,
    FILE_ERROR,
    UNKNOWN_EVENT;

    /**
     * Converte de forma resiliente uma representação textual no {@link EventType} correspondente.
     * Retorna {@link #UNKNOWN_EVENT} para valores nulos, vazios ou não mapeados, evitando quebras de fluxo.
     *
     * @param type texto recebido (ex: do payload MQTT ou HTTP)
     * @return o tipo mapeado ou {@link #UNKNOWN_EVENT}
     */
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
