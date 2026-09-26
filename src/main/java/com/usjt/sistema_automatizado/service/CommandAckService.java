package com.usjt.sistema_automatizado.service;

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
 *  4. O MqttRouterService usa o correlationId para resolver o Future exato.
 *
 * Isso garante que eventos físicos espontâneos (DOORBELL, PRESENCE_DETECTED)
 * que chegam no mesmo tópico /event NUNCA interferem com o ACK de um comando,
 * pois eles não carregam correlationId.
 */
@Slf4j
@Service
public class CommandAckService {

    // Timeout máximo de espera pelo ACK do firmware (em segundos)
    private static final long ACK_TIMEOUT_SECONDS = 5;

    // Chave: correlationId (UUID único por comando), Valor: Future aguardando ACK
    // ConcurrentHashMap garante thread-safety sem bloqueio desnecessário
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingAcks =
            new ConcurrentHashMap<>();

    /**
     * Registra a espera por um ACK para um correlationId específico.
     * Deve ser chamado ANTES de publicar o comando no MQTT.
     *
     * @param correlationId UUID único gerado pelo backend para este comando
     * @return O status final: "DELIVERED", "FAILED" ou "TIMEOUT"
     */
    public String aguardarAck(String correlationId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingAcks.put(correlationId, future);
        log.debug("[ACK] Aguardando confirmação para correlationId='{}' (timeout={}s)",
                correlationId, ACK_TIMEOUT_SECONDS);

        try {
            // Bloqueia a thread HTTP por até ACK_TIMEOUT_SECONDS aguardando resposta do firmware
            String status = future.get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("[ACK] Confirmação recebida para correlationId='{}': {}", correlationId, status);
            return status;
        } catch (TimeoutException e) {
            log.warn("[ACK] Timeout: correlationId='{}' não foi confirmado em {}s. " +
                    "O dispositivo pode estar lento ou offline.", correlationId, ACK_TIMEOUT_SECONDS);
            return "TIMEOUT";
        } catch (Exception e) {
            log.error("[ACK] Erro inesperado ao aguardar ACK para correlationId='{}': {}",
                    correlationId, e.getMessage(), e);
            return "FAILED";
        } finally {
            // Sempre limpa o mapa, mesmo em caso de erro ou timeout
            pendingAcks.remove(correlationId);
        }
    }

    /**
     * Resolve o Future de um correlationId com o status recebido via MQTT.
     * Chamado pelo MqttRouterService quando o firmware publica o ACK.
     *
     * @param correlationId UUID ecoado pelo firmware na confirmação
     * @param status        "DELIVERED" (COMMAND_SUCCESS) ou "FAILED" (COMMAND_FAILED)
     */
    public void resolverAck(String correlationId, String status) {
        if (correlationId == null || correlationId.isBlank()) {
            // Evento de confirmação sem correlationId (firmware antigo ou erro de payload):
            // não há Future para resolver — ignoramos silenciosamente.
            log.debug("[ACK] Confirmação recebida sem correlationId — ignorada (sem Future pendente).");
            return;
        }

        CompletableFuture<String> future = pendingAcks.get(correlationId);
        if (future != null) {
            future.complete(status);
            log.debug("[ACK] Future resolvido para correlationId='{}' com status '{}'", correlationId, status);
        } else {
            // Pode acontecer se o ACK chegar após o timeout já ter ocorrido
            log.debug("[ACK] Nenhum Future pendente para correlationId='{}' (pode ter expirado)", correlationId);
        }
    }
}
