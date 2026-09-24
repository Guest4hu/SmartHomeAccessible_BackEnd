package com.usjt.sistema_automatizado.repository;

import com.usjt.sistema_automatizado.model.entity.TelemetryReading;
import com.usjt.sistema_automatizado.model.enums.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, Long> {

    // 1. Busca os pontos para desenhar o gráfico (filtrado por período e ordenado por data)
    List<TelemetryReading> findByDeviceIdAndMetricAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long deviceId,
            MetricType metric,
            LocalDateTime from,
            LocalDateTime to
    );

    // 2. Busca apenas as métricas que o dispositivo já registou (para o dropdown do frontend)
    @Query("SELECT DISTINCT t.metric FROM TelemetryReading t WHERE t.device.id = :deviceId")
    List<MetricType> findDistinctMetricsByDeviceId(@Param("deviceId") Long deviceId);
}