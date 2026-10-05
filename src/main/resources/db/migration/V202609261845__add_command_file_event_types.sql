-- =====================================================================
-- V202609261845: Atualização dos tipos de eventos suportados
-- Suporte a confirmações tipadas de comando e arquivo (ACKs do firmware)
-- =====================================================================

ALTER TABLE event DROP CHECK ck_event_type;

ALTER TABLE event ADD CONSTRAINT ck_event_type CHECK (type IN (
    'DOORBELL',
    'PRESENCE_DETECTED',
    'HIGH_TEMPERATURE',
    'DEVICE_ONLINE',
    'DEVICE_OFFLINE',
    'COMMAND_SUCCESS',
    'COMMAND_FAILED',
    'FILE_RECEIVED',
    'FILE_ERROR',
    'UNKNOWN_EVENT'
));
