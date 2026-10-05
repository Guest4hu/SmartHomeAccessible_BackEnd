package com.usjt.sistema_automatizado.service.mqtt;

import com.usjt.sistema_automatizado.dto.MqttEnvelope;
import com.usjt.sistema_automatizado.model.enums.MqttMessageType;

/**
 * Contrato de estratégia (Strategy Pattern) para processadores de mensagens MQTT especializadas por tópico.
 */
public interface MqttMessageHandler {

    /**
     * Informa se a implementação é capaz de processar o tipo de mensagem indicado.
     *
     * @param messageType categoria da mensagem extraída do tópico
     * @return {@code true} se o handler suporta o tipo
     */
    boolean supports(MqttMessageType messageType);

    /**
     * Executa a regra de negócio associada ao envelope MQTT recebido.
     *
     * @param envelope mensagem encapsulada com tópico, identificador do dispositivo e payload
     * @throws Exception em caso de falha de parsing ou persistência
     */
    void handle(MqttEnvelope envelope) throws Exception;
}
