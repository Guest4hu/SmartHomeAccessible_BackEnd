package com.usjt.sistema_automatizado.model.enums;

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

    // Método utilitário que varre o tópico e descobre o tipo
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