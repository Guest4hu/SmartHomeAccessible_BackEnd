package com.usjt.sistema_automatizado.mapper;

import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.TelemetryReading;
import com.usjt.sistema_automatizado.model.enums.MetricType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapper responsável pela decomposição de medições agregadas no formato longo de séries temporais.
 */
@Component
public class TelemetryMapper {

    /**
     * Converte um {@link TelemetryRequest} composto em uma lista de entidades {@link TelemetryReading},
     * criando uma linha independente para cada grandeza medida (temperatura, umidade, luminosidade).
     *
     * @param request payload da leitura ambiental
     * @param device entidade do dispositivo correspondente
     * @return lista de leituras individuais prontas para persistência em lote
     */
    public List<TelemetryReading> toEntityList(TelemetryRequest request, Device device) {
        List<TelemetryReading> readings = new ArrayList<>();

        if (request == null || device == null) {
            return readings;
        }

        // Fatiamento do payload: Se o ESP32 enviou a temperatura, criamos uma linha para ela
        if (request.temperature() != null) {
            readings.add(createReading(device, MetricType.TEMPERATURE, request.temperature(), request.ts()));
        }

        // Se enviou a umidade, criamos outra linha separada
        if (request.humidity() != null) {
            readings.add(createReading(device, MetricType.HUMIDITY, request.humidity(), request.ts()));
        }

        // Se enviou a luminosidade, criamos a terceira linha
        if (request.luminosity() != null) {
            readings.add(createReading(device, MetricType.LUMINOSITY, request.luminosity(), request.ts()));
        }

        return readings;
    }

    private TelemetryReading createReading(Device device, MetricType metric, Double value, LocalDateTime recordedAt) {
        TelemetryReading reading = new TelemetryReading();
        reading.setDevice(device);
        reading.setMetric(metric);
        reading.setMetricValue(value);
        reading.setRecordedAt(recordedAt);
        return reading;
    }
}