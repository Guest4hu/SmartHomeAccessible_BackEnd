package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.model.enums.CommandDeliveryStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Serviço de correlação de ACK de comandos via correlationId.
 *
 * Padrão de mercado (Request-Response sobre MQTT assíncrono):
 *  1. O DeviceActionService gera um UUID único por requisição (correlationId).
 *  2. O correlationId é embutido no payload MQTT enviado ao firmware.
 *  3. O firmware ecoa o correlationId intacto na confirmação COMMAND_SUCCESS/FAILED.
 *  4. O EventMqttHandler usa o correlationId para resolver o Future exato.
 *
 * Isso garante que eventos físicos espontâneos (DOORBELL, PRESENCE_DETECTED)
 * que chegam no mesmo tópico /event NUNCA interferem com o ACK de um comando,
 * pois eles não carregam correlationId.
 */
@Slf4j
@Service
public class CommandAckService {

    // Timeout padrão de espera pelo ACK do firmware (em milissegundos: 5s)
    private static final long DEFAULT_ACK_TIMEOUT_MS = 5000;

    private final long ackTimeoutMs;

    // Chave: correlationId (UUID único por comando), Valor: Future aguardando ACK
    // ConcurrentHashMap garante thread-safety sem bloqueio desnecessário
    private final ConcurrentHashMap<String, CompletableFuture<CommandDeliveryStatus>> pendingAcks =
            new ConcurrentHashMap<>();

    // Chave: correlationId, Valor: externalId do dispositivo que deve responder
    private final ConcurrentHashMap<String, String> ackDeviceBindings =
            new ConcurrentHashMap<>();

    public CommandAckService() {
        this(DEFAULT_ACK_TIMEOUT_MS);
    }

    // Construtor auxiliar para testes unitários com timeout customizado
    CommandAckService(long ackTimeoutMs) {
        this.ackTimeoutMs = ackTimeoutMs;
    }

    /**
     * Registra preventivamente a espera de um ACK ANTES do comando ser publicado no MQTT.
     * Elimina race condition caso o dispositivo responda de forma ultra-rápida.
     *
     * @param correlationId UUID único gerado pelo backend para este comando
     * @param deviceExternalId identificador de hardware do ESP32 esperado
     */
    public void registrarEspera(String correlationId, String deviceExternalId) {
        if (correlationId != null && !correlationId.isBlank()) {
            pendingAcks.putIfAbsent(correlationId, new CompletableFuture<>());
            if (deviceExternalId != null && !deviceExternalId.isBlank()) {
                ackDeviceBindings.put(correlationId, deviceExternalId);
            }
        }
    }

    /**
     * Sobrecarga de conveniência para registro sem vínculo estrito de dispositivo.
     */
    public void registrarEspera(String correlationId) {
        registrarEspera(correlationId, null);
    }

    /**
     * Aguarda pelo ACK de um correlationId previamente registrado (ou registra e aguarda).
     *
     * @param correlationId UUID único gerado pelo backend para este comando
     * @return O status final: DELIVERED, FAILED ou TIMEOUT
     */
    public CommandDeliveryStatus aguardarAck(String correlationId) {
        CompletableFuture<CommandDeliveryStatus> future = pendingAcks.computeIfAbsent(
                correlationId, k -> new CompletableFuture<>());
        log.debug("[ACK] Aguardando confirmação para correlationId='{}' (timeout={}ms)",
                correlationId, ackTimeoutMs);

        try {
            // Bloqueia a thread HTTP por até ackTimeoutMs aguardando resposta do firmware
            CommandDeliveryStatus status = future.get(ackTimeoutMs, TimeUnit.MILLISECONDS);
            log.info("[ACK] Confirmação recebida para correlationId='{}': {}", correlationId, status);
            return status;
        } catch (TimeoutException e) {
            log.warn("[ACK] Timeout: correlationId='{}' não foi confirmado em {}ms. " +
                    "O dispositivo pode estar lento ou offline.", correlationId, ackTimeoutMs);
            return CommandDeliveryStatus.TIMEOUT;
        } catch (Exception e) {
            log.error("[ACK] Erro inesperado ao aguardar ACK para correlationId='{}': {}",
                    correlationId, e.getMessage(), e);
            return CommandDeliveryStatus.FAILED;
        } finally {
            // Sempre limpa os mapas, mesmo em caso de erro ou timeout
            pendingAcks.remove(correlationId);
            ackDeviceBindings.remove(correlationId);
        }
    }

    /**
     * Resolve o Future de um correlationId validando se o dispositivo emissor confere com o esperado.
     *
     * @param deviceExternalId identificador do dispositivo que enviou a confirmação
     * @param correlationId UUID ecoado pelo firmware na confirmação
     * @param status DELIVERED (COMMAND_SUCCESS) ou FAILED (COMMAND_FAILED)
     */
    public void resolverAck(String deviceExternalId, String correlationId, CommandDeliveryStatus status) {
        if (correlationId == null || correlationId.isBlank()) {
            log.debug("[ACK] Confirmação recebida sem correlationId — ignorada (sem Future pendente).");
            return;
        }

        String expectedDevice = ackDeviceBindings.get(correlationId);
        if (expectedDevice != null && deviceExternalId != null && !expectedDevice.equalsIgnoreCase(deviceExternalId)) {
            log.warn("[ACK] Dispositivo '{}' tentou responder pelo correlationId='{}' associado ao dispositivo '{}'!",
                    deviceExternalId, correlationId, expectedDevice);
            return;
        }

        resolverAck(correlationId, status);
    }

    /**
     * Resolve o Future de um correlationId com o status recebido via MQTT.
     * Chamado pelo EventMqttHandler quando o firmware publica o ACK.
     *
     * @param correlationId UUID ecoado pelo firmware na confirmação
     * @param status        DELIVERED (COMMAND_SUCCESS) ou FAILED (COMMAND_FAILED)
     */
    public void resolverAck(String correlationId, CommandDeliveryStatus status) {
        if (correlationId == null || correlationId.isBlank()) {
            // Evento de confirmação sem correlationId (firmware antigo ou erro de payload):
            // não há Future para resolver — ignoramos silenciosamente.
            log.debug("[ACK] Confirmação recebida sem correlationId — ignorada (sem Future pendente).");
            return;
        }

        CompletableFuture<CommandDeliveryStatus> future = pendingAcks.get(correlationId);
        if (future != null) {
            future.complete(status);
            log.debug("[ACK] Future resolvido para correlationId='{}' com status '{}'", correlationId, status);
        } else {
            // Pode acontecer se o ACK chegar após o timeout já ter ocorrido
            log.debug("[ACK] Nenhum Future pendente para correlationId='{}' (pode ter expirado)", correlationId);
        }
    }

    /**
     * Sobrecarga de conveniência para retrocompatibilidade com chamadas baseadas em String.
     *
     * @param correlationId UUID ecoado pelo firmware na confirmação
     * @param status        "DELIVERED" ou "FAILED" em String
     */
    public void resolverAck(String correlationId, String status) {
        resolverAck(correlationId, CommandDeliveryStatus.fromString(status));
    }

    /**
     * Retorna a quantidade de acks atualmente pendentes (visibilidade para testes).
     */
    int getPendingAcksCount() {
        return pendingAcks.size();
    }
}
