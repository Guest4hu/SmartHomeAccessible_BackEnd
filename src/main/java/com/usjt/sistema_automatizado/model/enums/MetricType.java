package com.usjt.sistema_automatizado.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Grandezas físicas ambientais capturadas pelos sensores conectados ao microcontrolador.
 */
@Getter
@RequiredArgsConstructor
public enum MetricType {

    TEMPERATURE("Temperatura", "°C"),
    HUMIDITY("Umidade", "%"),
    LUMINOSITY("Luminosidade", "lux");

    private final String label;
    private final String unit;
}
