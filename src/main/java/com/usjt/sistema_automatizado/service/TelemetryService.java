package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.MetricInfoResponse;
import com.usjt.sistema_automatizado.dto.response.MetricSeriesResponse;
import com.usjt.sistema_automatizado.mapper.TelemetryMapper;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.TelemetryReading;
import com.usjt.sistema_automatizado.model.enums.MetricType;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.TelemetryReadingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final TelemetryReadingRepository telemetryRepository;
    private final DeviceRepository deviceRepository; // Usado diretamente aqui para simplificar a busca
    private final TelemetryMapper telemetryMapper;

    @Transactional
    public void saveTelemetry(TelemetryRequest request) {
        Device device = deviceRepository.findByExternalId(request.deviceId())
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo não encontrado: " + request.deviceId()));

        List<TelemetryReading> readings = telemetryMapper.toEntityList(request, device);

        if (!readings.isEmpty()) {
            telemetryRepository.saveAll(readings);
        }
    }

    @Transactional(readOnly = true)
    public List<MetricInfoResponse> getAvailableMetrics(Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new EntityNotFoundException("Dispositivo não encontrado.");
        }

        return telemetryRepository.findDistinctMetricsByDeviceId(deviceId)
                .stream()
                .map(metric -> new MetricInfoResponse(metric, metric.getLabel(), metric.getUnit()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MetricSeriesResponse getMetricSeries(Long deviceId, MetricType metric, LocalDateTime from, LocalDateTime to, String interval) {
        // 1. Resolução do período (padrão: últimas 24 horas)
        LocalDateTime end = (to != null) ? to : LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime start = (from != null) ? from : end.minusHours(24);

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("A data de início (from) não pode ser posterior à data de fim (to).");
        }

        // 2. Resolução automática do intervalo
        String resolvedInterval = resolveInterval(interval, start, end);

        // 3. Busca dos dados brutos
        List<TelemetryReading> readings = telemetryRepository
                .findByDeviceIdAndMetricAndRecordedAtBetweenOrderByRecordedAtAsc(deviceId, metric, start, end);

        // 4. Agregação dos dados
        List<MetricSeriesResponse.Point> points = aggregateReadings(readings, resolvedInterval);

        return new MetricSeriesResponse(
                deviceId,
                metric,
                metric.getUnit(),
                start,
                end,
                resolvedInterval,
                points
        );
    }

    // Regra de negócio: Escolhe RAW, HOUR ou DAY com base no tamanho do período
    private String resolveInterval(String requested, LocalDateTime start, LocalDateTime end) {
        if (requested != null && !requested.isBlank()) {
            return requested.toUpperCase();
        }

        long hours = Duration.between(start, end).toHours();
        if (hours <= 24) return "RAW";
        if (hours <= 24 * 7) return "HOUR";

        return "DAY";
    }

    // O "motor" matemático para agregar milhares de leituras em poucos pontos para o gráfico
    private List<MetricSeriesResponse.Point> aggregateReadings(List<TelemetryReading> readings, String interval) {
        if (readings.isEmpty()) {
            return List.of();
        }

        // Se for RAW, devolvemos a leitura exata (média, min e max são iguais)
        if ("RAW".equals(interval)) {
            return readings.stream()
                    .map(r -> new MetricSeriesResponse.Point(
                            r.getRecordedAt(),
                            r.getMetricValue(),
                            r.getMetricValue(),
                            r.getMetricValue()))
                    .toList();
        }

        // Função que "arredonda" a data para a hora ou dia correspondente
        Function<TelemetryReading, LocalDateTime> grouper = r -> "DAY".equals(interval)
                ? r.getRecordedAt().truncatedTo(ChronoUnit.DAYS)
                : r.getRecordedAt().truncatedTo(ChronoUnit.HOURS);

        // Agrupa, calcula as estatísticas e converte para a lista final ordenada
        return readings.stream()
                .collect(Collectors.groupingBy(grouper))
                .entrySet().stream()
                .map(entry -> {
                    LocalDateTime timeKey = entry.getKey();

                    // O DoubleSummaryStatistics calcula a média, min e max numa única passagem!
                    DoubleSummaryStatistics stats = entry.getValue().stream()
                            .mapToDouble(TelemetryReading::getMetricValue)
                            .summaryStatistics();

                    return new MetricSeriesResponse.Point(timeKey, stats.getAverage(), stats.getMin(), stats.getMax());
                })
                .sorted(Comparator.comparing(MetricSeriesResponse.Point::t))
                .toList();
    }
}