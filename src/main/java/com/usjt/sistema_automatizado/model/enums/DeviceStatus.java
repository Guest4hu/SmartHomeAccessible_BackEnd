package com.usjt.sistema_automatizado.model.enums;


/**
 * Estado de conectividade do dispositivo no sistema.
 *
 * <p>Sincronizado principalmente por mensagens Last Will and Testament (LWT) publicadas no tópico
 * de status MQTT na conexão e desconexão (inclusive abrupta) do microcontrolador.</p>
 */
public enum DeviceStatus
{
    ONLINE, OFFLINE
}
