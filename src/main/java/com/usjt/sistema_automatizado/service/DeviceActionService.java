package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.CommandRequest;
import com.usjt.sistema_automatizado.dto.request.TelemetryRequest;
import com.usjt.sistema_automatizado.dto.response.CommandResponse;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.mapper.DeviceActionMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.enums.CommandDeliveryStatus;
import com.usjt.sistema_automatizado.model.enums.CommandType;
import com.usjt.sistema_automatizado.model.enums.DeviceStatus;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Serviço responsável pela orquestração do despacho de comandos manuais para o hardware (ESP32).
 *
 * <p>Implementa o <b>Correlation Pattern</b> para sincronizar o ciclo síncrono HTTP com a mensageria
 * assíncrona MQTT, protegendo o hardware contra sobrecargas e garantindo rastreabilidade de entrega.</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceActionService {

    private final DeviceRepository deviceRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final AppUserRepository appUserRepository;
    private final DeviceActionMapper commandMapper;
    private final MqttService mqttService;
    private final CommandAckService commandAckService;
    private final DeviceService deviceService;

    /**
     * Envia um comando operacional para o microcontrolador via MQTT e bloqueia até receber a confirmação de entrega.
     *
     * <p><b>Decisão arquitetural:</b> Este método <i>propositalmente não utiliza {@code @Transactional}</i>.
     * Como a thread HTTP aguarda síncronamente o ACK do firmware por até 5 segundos via
     * {@link CommandAckService#aguardarAck(String)}, manter uma transação JPA ativa durante essa espera
     * prenderia uma conexão do pool HikariCP desnecessariamente, comprometendo a escalabilidade.</p>
     *
     * <p><b>Proteção de hardware:</b> Rejeita requisições se o status do dispositivo for {@code OFFLINE},
     * prevenindo timeouts e o enfileiramento de mensagens em dispositivos desconectados.</p>
     *
     * @param deviceId identificador interno do dispositivo alvo
     * @param request dados do comando a ser executado
     * @param requesterId identificador do usuário autenticado solicitante
     * @return resposta estruturada contendo o status final de entrega ({@code DELIVERED}, {@code FAILED} ou {@code TIMEOUT})
     * @throws jakarta.persistence.EntityNotFoundException se o dispositivo ou usuário solicitante não existirem
     * @throws IllegalArgumentException se o dispositivo estiver OFFLINE ou se o usuário não pertencer à residência
     */
    public CommandResponse sendCommand(Long deviceId, CommandRequest request, Long requesterId) {
        // 1. Busca o dispositivo
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo não encontrado."));

        // 2. Segurança: Garante primeiro que o utilizador pertence à casa antes de revelar status
        homeMemberRepository.findByHomeIdAndUserId(device.getHome().getId(), requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso aos dispositivos desta casa."));

        // 3. Validação do Comando: Rejeita comandos não suportados pelo firmware
        if (request.type() == CommandType.SET_VALUE) {
            throw new IllegalArgumentException("Tipo de comando 'SET_VALUE' não suportado.");
        }

        // 4. Proteção de Hardware: Bloqueia comandos se o ESP32 estiver offline
        if (device.getStatus() == DeviceStatus.OFFLINE) {
            throw new IllegalArgumentException(
                    "Não é possível enviar o comando. O dispositivo '" + device.getName() + "' está OFFLINE, Ative o No Aplicativo."
            );
        }

        AppUser requester = appUserRepository.findById(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("Utilizador não encontrado."));

        // 5. Gera um UUID único para esta requisição (correlationId).
        String correlationId = UUID.randomUUID().toString();

        // 6. Registra preventivamente no CommandAckService ANTES do envio MQTT
        //    para eliminar race conditions com respostas instantâneas
        commandAckService.registrarEspera(correlationId, device.getExternalId());

        // 7. Converte o request para JSON com o correlationId embutido
        String jsonPayload = commandMapper.toCommandJson(request, correlationId);

        // 8. Publica o comando no MQTT
        mqttService.sendCommand(device.getExternalId(), jsonPayload);

        // 9. Aguarda a confirmação do firmware pelo correlationId específico (até 5s)
        CommandDeliveryStatus ackStatus = commandAckService.aguardarAck(correlationId);

        // 10. Retorna a resposta com o status real de entrega ao dispositivo
        return new CommandResponse(
                device.getExternalId(),
                request.type(),
                ackStatus,
                LocalDateTime.now(ZoneOffset.UTC),
                requester.getName()
        );
    }

    /**
     * Atualiza o status do dispositivo com validação de morador da residência.
     */
    public DeviceResponse processHeartbeat(TelemetryRequest request, Long requesterId) {
        return deviceService.processHeartbeat(request, requesterId);
    }

    /**
     * Fachada delegante para preservar retrocompatibilidade de API e chamadas de controllers.
     * A regra de negócio de registro de heartbeat é executada por DeviceService.
     */
    public DeviceResponse processHeartbeat(TelemetryRequest request) {
        return deviceService.processHeartbeat(request);
    }
}