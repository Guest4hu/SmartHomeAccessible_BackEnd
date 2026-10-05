-- =====================================================================
-- V202609231603: dados que crescem com o tempo (telemetria e eventos)
-- =====================================================================

-- Leituras dos sensores em formato "longo": UMA LINHA POR MÉTRICA.
-- Uma mensagem do ESP32 com temperatura, umidade e luminosidade vira 3 linhas,
-- todas com o mesmo recorded_at. Presença NÃO entra aqui: ela é um evento
-- (event.type = 'PRESENCE_DETECTED').
CREATE TABLE telemetry_reading (
                                   id            BIGINT AUTO_INCREMENT PRIMARY KEY,   -- o JPA exige um @Id
                                   device_id     BIGINT      NOT NULL,
                                   metric        VARCHAR(20) NOT NULL,                -- TEMPERATURE, HUMIDITY, LUMINOSITY
                                   metric_value  DOUBLE      NOT NULL,                -- evita o nome "value" (função do JPQL)
                                   recorded_at   DATETIME(3) NOT NULL,                -- horário medido pelo ESP32 (UTC)
                                   received_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),  -- quando o backend recebeu

                                   CONSTRAINT fk_telemetry_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE,
                                   CONSTRAINT ck_telemetry_metric CHECK (metric IN ('TEMPERATURE', 'HUMIDITY', 'LUMINOSITY')),

    -- Impede a mesma leitura duplicada (ex.: ESP32 reenviando após queda de rede).
    -- Também é o índice da consulta do gráfico:
    -- WHERE device_id = ? AND metric = ? AND recorded_at BETWEEN ? AND ?
                                   CONSTRAINT uk_telemetry_device_metric_time UNIQUE (device_id, metric, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Eventos pontuais: campainha, presença, alertas, queda de dispositivo
CREATE TABLE event (
                       id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                       device_id        BIGINT      NOT NULL,
                       type             VARCHAR(30) NOT NULL,
                       occurred_at      DATETIME(3) NOT NULL,   -- quando aconteceu (informado pelo ESP32 ou pelo backend)
                       received_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                       acknowledged_at  DATETIME(3) NULL,       -- quando alguém confirmou que viu o alerta
                       acknowledged_by  BIGINT      NULL,

                       CONSTRAINT fk_event_device FOREIGN KEY (device_id)       REFERENCES device (id) ON DELETE CASCADE,
                       CONSTRAINT fk_event_user   FOREIGN KEY (acknowledged_by) REFERENCES app_user (id),
                       CONSTRAINT ck_event_type   CHECK (type IN (
                                                                  'DOORBELL',
                                                                  'PRESENCE_DETECTED',
                                                                  'HIGH_TEMPERATURE',
                                                                  'DEVICE_ONLINE',
                                                                  'DEVICE_OFFLINE'
                           ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_event_device_time ON event (device_id, occurred_at);
CREATE INDEX idx_event_type_time   ON event (type, occurred_at);