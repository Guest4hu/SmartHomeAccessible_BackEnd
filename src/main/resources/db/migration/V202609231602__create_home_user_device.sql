-- =====================================================================
-- V1: estrutura base (casa, usuários, dispositivos e configuração)
-- MySQL 8.0.16+ (necessário para o CHECK ser realmente aplicado)
-- Datas em UTC, com precisão de milissegundos: DATETIME(3)
-- =====================================================================

CREATE TABLE home (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE app_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    home_id        BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,          -- BCrypt, nunca senha pura
    role           VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT fk_app_user_home  FOREIGN KEY (home_id) REFERENCES home (id),
    CONSTRAINT ck_app_user_role  CHECK (role IN ('ADMIN', 'MEMBER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE device (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    home_id           BIGINT       NOT NULL,
    external_id       VARCHAR(50)  NOT NULL,        -- o deviceId que o ESP32 envia, ex.: "esp32-sala-01"
    name              VARCHAR(100) NOT NULL,
    room              VARCHAR(50)  NULL,            -- ex.: "Sala", "Quarto"
    status            VARCHAR(10)  NOT NULL DEFAULT 'OFFLINE',
    last_seen_at      DATETIME(3)  NULL,            -- última mensagem recebida
    firmware_version  VARCHAR(20)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT uk_device_external_id UNIQUE (external_id),
    CONSTRAINT fk_device_home        FOREIGN KEY (home_id) REFERENCES home (id),
    CONSTRAINT ck_device_status      CHECK (status IN ('ONLINE', 'OFFLINE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_device_home ON device (home_id);

-- Configuração de automação: 1 linha por dispositivo
CREATE TABLE automation_config (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id         BIGINT       NOT NULL,
    fan_on_above      DECIMAL(4,1) NOT NULL DEFAULT 28.0,   -- liga o ventilador acima de (°C)
    fan_off_below     DECIMAL(4,1) NOT NULL DEFAULT 26.0,   -- desliga abaixo de (°C)
    dark_below        INT          NOT NULL DEFAULT 200,    -- leitura do LDR considerada "escuro"
    doorbell_pattern  VARCHAR(30)  NOT NULL DEFAULT 'BLUE_PULSE',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                   ON UPDATE CURRENT_TIMESTAMP(3),
    updated_by        BIGINT       NULL,

    CONSTRAINT uk_automation_device    UNIQUE (device_id),
    CONSTRAINT fk_automation_device    FOREIGN KEY (device_id)  REFERENCES device (id) ON DELETE CASCADE,
    CONSTRAINT fk_automation_user      FOREIGN KEY (updated_by) REFERENCES app_user (id),
    -- histerese: o limite de ligar precisa ser MAIOR que o de desligar
    CONSTRAINT ck_automation_hysteresis CHECK (fan_on_above > fan_off_below)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
