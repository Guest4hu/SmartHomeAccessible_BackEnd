package com.usjt.sistema_automatizado.config.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[]{brokerUrl});
        options.setCleanSession(true);

        // 1. AUTO RECONNECT: Se o broker derrubar (EOFException), o Java tenta reconectar sozinho!
        options.setAutomaticReconnect(true);

        // 2. KEEP ALIVE: O Java manda um "PING" a cada 60 segundos para o broker não o considerar ocioso
        options.setKeepAliveInterval(60);

        // 3. TIMEOUT: Se a internet falhar, ele desiste em 10 segundos em vez de travar a thread
        options.setConnectionTimeout(10);

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(clientId, mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic("default/topic");
        return messageHandler;
    }
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel(); // Canal interno do Spring para onde as mensagens vão escorrer
    }

    @Bean
    public MessageProducer inbound() {
        // Criamos um ClientID diferente para a escuta (adicionamos o sufixo -rx)
        // O símbolo "+" é o wildcard do MQTT: significa "escuta QUALQUER deviceId"
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-rx", mqttClientFactory(),
                        "devices/+/telemetry", "devices/+/event");

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }
}