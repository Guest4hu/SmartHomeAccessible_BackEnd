package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.TelemetryReading;
import com.usjt.sistema_automatizado.model.enums.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositório de acesso a dados para leituras de séries temporais de sensores ambientais.
 */
@Repository
public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, Long> {

    /**
     * Recupera os pontos de leitura ordenados cronologicamente para composição de gráficos e agregações.
     *
     * @param deviceId identificador interno do dispositivo
     * @param metric tipo de métrica consultada (ex: TEMPERATURE, HUMIDITY)
     * @param from limite inicial do intervalo temporal em UTC
     * @param to limite final do intervalo temporal em UTC
     * @return lista ordenada de leituras de telemetria
     */
    List<TelemetryReading> findByDeviceIdAndMetricAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long deviceId,
            MetricType metric,
            LocalDateTime from,
            LocalDateTime to
    );

    /**
     * Retorna os tipos distintos de métricas já registrados pelo dispositivo para popular filtros de interface.
     *
     * @param deviceId identificador interno do dispositivo
     * @return lista de tipos de métricas com histórico no banco
     */
    @Query("SELECT DISTINCT t.metric FROM TelemetryReading t WHERE t.device.id = :deviceId")
    List<MetricType> findDistinctMetricsByDeviceId(@Param("deviceId") Long deviceId);
}