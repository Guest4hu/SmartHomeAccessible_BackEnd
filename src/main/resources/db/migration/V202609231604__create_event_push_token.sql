-- =====================================================================
-- V3: tokens do Firebase Cloud Messaging (push)
-- Um usuário pode ter vários tokens: navegador, celular Android, iPhone...
-- =====================================================================

CREATE TABLE push_token (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    token         VARCHAR(512) NOT NULL,
    platform      VARCHAR(10)  NOT NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_used_at  DATETIME(3)  NULL,

    CONSTRAINT uk_push_token          UNIQUE (token),
    CONSTRAINT fk_push_token_user     FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_push_token_platform CHECK (platform IN ('WEB', 'ANDROID', 'IOS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_push_token_user ON push_token (user_id);
