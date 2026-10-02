package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.model.enums.CommandDeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class CommandAckServiceTest {

    private CommandAckService commandAckService;

    @BeforeEach
    void setUp() {
        commandAckService = new CommandAckService(2000);
    }

    @Test
    void aguardarAck_DeveRetornarDelivered_QuandoAckResolvidoComSucesso() throws Exception {
        String correlationId = "corr-success-01";

        CompletableFuture<CommandDeliveryStatus> asyncResult =
                CompletableFuture.supplyAsync(() -> commandAckService.aguardarAck(correlationId));

        // Aguarda breve instante para garantir que a espera foi registrada
        Thread.sleep(50);
        commandAckService.resolverAck(correlationId, CommandDeliveryStatus.DELIVERED);

        CommandDeliveryStatus status = asyncResult.get(1, TimeUnit.SECONDS);
        assertEquals(CommandDeliveryStatus.DELIVERED, status);
        assertEquals(0, commandAckService.getPendingAcksCount());
    }

    @Test
    void aguardarAck_DeveRetornarFailed_QuandoAckResolvidoComFalha() throws Exception {
        String correlationId = "corr-failed-01";

        CompletableFuture<CommandDeliveryStatus> asyncResult =
                CompletableFuture.supplyAsync(() -> commandAckService.aguardarAck(correlationId));

        Thread.sleep(50);
        commandAckService.resolverAck(correlationId, CommandDeliveryStatus.FAILED);

        CommandDeliveryStatus status = asyncResult.get(1, TimeUnit.SECONDS);
        assertEquals(CommandDeliveryStatus.FAILED, status);
        assertEquals(0, commandAckService.getPendingAcksCount());
    }

    @Test
    void resolverAck_DeveSuportarSobrecargaComString() throws Exception {
        String correlationId = "corr-string-01";

        CompletableFuture<CommandDeliveryStatus> asyncResult =
                CompletableFuture.supplyAsync(() -> commandAckService.aguardarAck(correlationId));

        Thread.sleep(50);
        commandAckService.resolverAck(correlationId, "DELIVERED");

        CommandDeliveryStatus status = asyncResult.get(1, TimeUnit.SECONDS);
        assertEquals(CommandDeliveryStatus.DELIVERED, status);
    }

    @Test
    void resolverAck_DeveInterpretarStringDesconhecidaComoFailed() throws Exception {
        String correlationId = "corr-string-unk";

        CompletableFuture<CommandDeliveryStatus> asyncResult =
                CompletableFuture.supplyAsync(() -> commandAckService.aguardarAck(correlationId));

        Thread.sleep(50);
        commandAckService.resolverAck(correlationId, "STATUS_INVALIDO");

        CommandDeliveryStatus status = asyncResult.get(1, TimeUnit.SECONDS);
        assertEquals(CommandDeliveryStatus.FAILED, status);
    }

    @Test
    void aguardarAck_DeveRetornarTimeoutELimparMapa_QuandoExcederTempoLimite() {
        CommandAckService shortTimeoutService = new CommandAckService(80);
        String correlationId = "corr-timeout-01";

        CommandDeliveryStatus status = shortTimeoutService.aguardarAck(correlationId);

        assertEquals(CommandDeliveryStatus.TIMEOUT, status);
        assertEquals(0, shortTimeoutService.getPendingAcksCount());
    }

    @Test
    void resolverAck_DeveIgnorarQuandoCorrelationIdForNuloOuVazio() {
        assertDoesNotThrow(() -> commandAckService.resolverAck(null, CommandDeliveryStatus.DELIVERED));
        assertDoesNotThrow(() -> commandAckService.resolverAck("", CommandDeliveryStatus.DELIVERED));
        assertDoesNotThrow(() -> commandAckService.resolverAck("   ", CommandDeliveryStatus.DELIVERED));
        assertDoesNotThrow(() -> commandAckService.resolverAck(null, "DELIVERED"));
    }

    @Test
    void resolverAck_DeveIgnorarQuandoNaoExistirFuturePendente() {
        assertDoesNotThrow(() -> commandAckService.resolverAck("corr-inexistente", CommandDeliveryStatus.DELIVERED));
        assertEquals(0, commandAckService.getPendingAcksCount());
    }
}
