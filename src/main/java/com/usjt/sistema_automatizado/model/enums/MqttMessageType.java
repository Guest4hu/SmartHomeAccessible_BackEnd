package com.usjt.sistema_automatizado.model.enums;

/**
 * Categorias de mensagens e sufixos de tópicos MQTT suportados pelo sistema.
 * Utilizado pelo roteador para encaminhar payloads ao processador especializado correspondente.
 */
public enum MqttMessageType {
    TELEMETRY("telemetry"),
    EVENT("event"),
    STATUS("status"),
    COMMAND("cmd"),
    UNKNOWN("");

    private final String suffix;

    MqttMessageType(String suffix) {
        this.suffix = suffix;
    }

    /**
     * Identifica a categoria da mensagem inspecionando o sufixo do tópico MQTT recebido.
     *
     * @param topic tópico completo publicado no broker
     * @return o {@link MqttMessageType} correspondente ou {@link #UNKNOWN}
     */
    public static MqttMessageType fromTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return UNKNOWN;
        }

        for (MqttMessageType type : values()) {
            // Verifica se o tópico termina com "/telemetry", "/event", etc.
            if (type != UNKNOWN && topic.endsWith("/" + type.suffix)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public String getSuffix() {
        return suffix;
    }
}