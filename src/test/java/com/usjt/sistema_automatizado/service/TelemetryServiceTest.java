package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.MetricInfoResponse;
import com.usjt.sistema_automatizado.dto.response.MetricSeriesResponse;
import com.usjt.sistema_automatizado.mapper.TelemetryMapper;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.TelemetryReading;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.model.enums.MetricType;
import com.usjt.sistema_automatizado.model.enums.SensoryChannel;
import com.usjt.sistema_automatizado.model.enums.UrgencyLevel;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.TelemetryReadingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock
    private TelemetryReadingRepository telemetryRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private TelemetryMapper telemetryMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private TelemetryService telemetryService;

    private Device device;

    @BeforeEach
    void setUp() {
        device = new Device();
        device.setId(1L);
        device.setExternalId("esp32-telemetry-01");
        device.setName("Sensor Quarto");
        device.setStatus(DeviceStatus.OFFLINE);
    }

    @Test
    void saveTelemetry_DeveSalvarLeiturasEAtualizarLastSeenAtEStatus() {
        TelemetryRequest request = new TelemetryRequest("esp32-telemetry-01", 25.0, 50.0, 300.0);
        TelemetryReading reading = new TelemetryReading();

        when(deviceRepository.findByExternalId("esp32-telemetry-01")).thenReturn(Optional.of(device));
        when(telemetryMapper.toEntityList(request, device)).thenReturn(List.of(reading));

        telemetryService.saveTelemetry(request);

        assertEquals(DeviceStatus.ONLINE, device.getStatus());
        assertNotNull(device.getLastSeenAt());
        verify(deviceRepository, times(1)).save(device);
        verify(telemetryRepository, times(1)).saveAll(List.of(reading));
        verifyNoInteractions(notificationService);
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void saveTelemetry_DeveDispararAlertaCriticoEPush_QuandoTemperaturaAcimaDe45() {
        TelemetryRequest request = new TelemetryRequest("esp32-telemetry-01", 48.5, 30.0, 400.0);

        when(deviceRepository.findByExternalId("esp32-telemetry-01")).thenReturn(Optional.of(device));
        when(telemetryMapper.toEntityList(request, device)).thenReturn(List.of());
        when(deviceRepository.findHomeIdByExternalId("esp32-telemetry-01")).thenReturn(Optional.of(1L));

        telemetryService.saveTelemetry(request);

        verify(notificationService, times(1)).dispatchEvent(
                eq("sensor-alert"),
                eq("esp32-telemetry-01"),
                eq("CRITICAL_TEMPERATURE"),
                eq(UrgencyLevel.CRITICAL),
                eq(SensoryChannel.MULTIMODAL),
                anyString(),
                anyString()
        );

        verify(pushNotificationService, times(1)).sendNotificationToHome(
                eq(1L),
                eq("Alerta Crítico: Temperatura Alta"),
                contains("48.5"),
                anyMap()
        );
    }

    @Test
    void saveTelemetry_DeveDispararAvisoCongelamentoEPush_QuandoTemperaturaAbaixoDeZero() {
        TelemetryRequest request = new TelemetryRequest("esp32-telemetry-01", -2.0, 80.0, 100.0);

        when(deviceRepository.findByExternalId("esp32-telemetry-01")).thenReturn(Optional.of(device));
        when(telemetryMapper.toEntityList(request, device)).thenReturn(List.of());
        when(deviceRepository.findHomeIdByExternalId("esp32-telemetry-01")).thenReturn(Optional.of(1L));

        telemetryService.saveTelemetry(request);

        verify(notificationService, times(1)).dispatchEvent(
                eq("sensor-alert"),
                eq("esp32-telemetry-01"),
                eq("FREEZE_WARNING"),
                eq(UrgencyLevel.WARNING),
                eq(SensoryChannel.MULTIMODAL),
                anyString(),
                anyString()
        );

        verify(pushNotificationService, times(1)).sendNotificationToHome(
                eq(1L),
                eq("Alerta: Risco de Congelamento"),
                contains("-2.0"),
                anyMap()
        );
    }

    @Test
    void saveTelemetry_DeveLancarExcecao_QuandoDispositivoNaoEncontrado() {
        TelemetryRequest request = new TelemetryRequest("esp32-inexistente", 24.0, 50.0, 200.0);
        when(deviceRepository.findByExternalId("esp32-inexistente")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> telemetryService.saveTelemetry(request));
        verify(telemetryRepository, never()).saveAll(any());
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void getAvailableMetrics_DeveRetornarMetricasDisponiveis() {
        when(deviceRepository.existsById(1L)).thenReturn(true);
        when(telemetryRepository.findDistinctMetricsByDeviceId(1L))
                .thenReturn(List.of(MetricType.TEMPERATURE, MetricType.HUMIDITY));

        List<MetricInfoResponse> metrics = telemetryService.getAvailableMetrics(1L);

        assertEquals(2, metrics.size());
        assertEquals(MetricType.TEMPERATURE, metrics.get(0).metric());
        assertEquals(MetricType.HUMIDITY, metrics.get(1).metric());
    }

    @Test
    void getAvailableMetrics_DeveLancarExcecao_QuandoDispositivoNaoExiste() {
        when(deviceRepository.existsById(99L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> telemetryService.getAvailableMetrics(99L));
    }

    @Test
    void getMetricSeries_DeveLancarExcecao_QuandoDataInicioPosteriorADataFim() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicioFuturo = agora.plusHours(2);

        assertThrows(IllegalArgumentException.class, () ->
                telemetryService.getMetricSeries(1L, MetricType.TEMPERATURE, inicioFuturo, agora, "RAW"));
    }

    @Test
    void getMetricSeries_DeveRetornarSerieAgregadaComSucesso() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicio = agora.minusHours(2);

        TelemetryReading r1 = new TelemetryReading();
        r1.setRecordedAt(inicio.plusMinutes(10));
        r1.setMetricValue(24.0);

        TelemetryReading r2 = new TelemetryReading();
        r2.setRecordedAt(inicio.plusMinutes(20));
        r2.setMetricValue(26.0);

        when(telemetryRepository.findByDeviceIdAndMetricAndRecordedAtBetweenOrderByRecordedAtAsc(
                eq(1L), eq(MetricType.TEMPERATURE), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(r1, r2));

        MetricSeriesResponse response = telemetryService.getMetricSeries(1L, MetricType.TEMPERATURE, inicio, agora, "RAW");

        assertNotNull(response);
        assertEquals(1L, response.deviceId());
        assertEquals(MetricType.TEMPERATURE, response.metric());
        assertEquals(2, response.points().size());
        assertEquals(24.0, response.points().get(0).avg());
        assertEquals(26.0, response.points().get(1).avg());
    }
}
