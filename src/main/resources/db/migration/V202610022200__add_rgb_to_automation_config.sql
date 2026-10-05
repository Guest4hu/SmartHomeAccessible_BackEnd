-- =====================================================================
-- V202610022200: adiciona campos de cor RGB da campainha à automation_config
-- bellR, bellG, bellB: componentes individuais (0–255) da cor do LED RGB
-- configurável pelo ADMIN pelo dashboard — enviado ao firmware via MQTT /config
--
-- Tipo INT (não TINYINT UNSIGNED): o Hibernate valida que a coluna do banco
-- é compatível com java.lang.Integer — TINYINT UNSIGNED mapeia para Types#TINYINT,
-- causando SchemaManagementException na validação. INT mapeia para Types#INTEGER.
-- A restrição de valores 0–255 é garantida pela validação @Min/@Max no DTO.
-- =====================================================================
ALTER TABLE automation_config
    ADD COLUMN bell_r INT NOT NULL DEFAULT 255 AFTER doorbell_pattern,
    ADD COLUMN bell_g INT NOT NULL DEFAULT 0   AFTER bell_r,
    ADD COLUMN bell_b INT NOT NULL DEFAULT 0   AFTER bell_g;
