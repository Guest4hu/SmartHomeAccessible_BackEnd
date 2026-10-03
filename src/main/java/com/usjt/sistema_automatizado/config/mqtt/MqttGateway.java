package com.usjt.sistema_automatizado.config.mqtt;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;

/**
 * Gateway de mensageria Spring Integration para publicação dinâmica em tópicos MQTT.
 */
@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
public interface MqttGateway {

    /**
     * Publica uma mensagem no tópico MQTT especificado.
     *
     * @param topic tópico MQTT de destino (ex: {@code devices/{externalId}/cmd})
     * @param payload conteúdo textual ou JSON serializado
     */
    void sendToMqtt(@Header(MqttHeaders.TOPIC) String topic, String payload);
}