package com.usjt.sistema_automatizado.service;

import java.util.Map;

/**
 * Contrato de serviço para despacho de notificações push móveis via Firebase Cloud Messaging (FCM).
 *
 * <p>Garante isolamento multi-tenant residencial, fallback resiliente para modo Dry-Run/Mock
 * e suporte a eventos do sistema (campainha, anomalias ambientais, status de dispositivos).</p>
 */
public interface PushNotificationService {

    /**
     * Despacha uma notificação push para todos os dispositivos registrados pelos moradores
     * pertencentes à residência especificada.
     *
     * @param homeId identificador único da residência
     * @param title título da notificação
     * @param body mensagem detalhada do alerta ou evento
     * @param data mapa chave-valor com metadados adicionais (ex: eventType, deviceId)
     */
    void sendNotificationToHome(Long homeId, String title, String body, Map<String, String> data);

    /**
     * Despacha uma notificação push direcionada exclusivamente aos dispositivos de um usuário específico.
     *
     * @param userId identificador do usuário
     * @param title título da notificação
     * @param body mensagem detalhada
     * @param data metadados adicionais
     */
    void sendNotificationToUser(Long userId, String title, String body, Map<String, String> data);

    /**
     * Verifica se o serviço está operando em modo de simulação (Dry-Run / Mock).
     *
     * @return true se o modo Dry-Run estiver ativo ou se o Firebase não estiver configurado; false caso contrário.
     */
    boolean isDryRun();

    /**
     * Despacha uma notificação de evento especializada para os moradores da residência especificada.
     *
     * @param homeId identificador da residência
     * @param eventType tipo de evento (ex: DOORBELL, CLIMATE_ANOMALY, DEVICE_OFFLINE)
     * @param deviceId identificador do dispositivo de origem
     * @param title título da notificação
     * @param body mensagem detalhada do alerta ou evento
     */
    default void sendEventNotification(Long homeId, String eventType, String deviceId, String title, String body) {
        java.util.Map<String, String> data = new java.util.HashMap<>();
        if (eventType != null) {
            data.put("eventType", eventType);
        }
        if (deviceId != null) {
            data.put("deviceId", deviceId);
        }
        sendNotificationToHome(homeId, title, body, data);
    }
}

