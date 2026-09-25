package com.usjt.sistema_automatizado.model.enums;

public enum CommandType {
    TURN_ON,       // Ex: Ligar a ventilação manualmente
    TURN_OFF,      // Ex: Desligar a ventilação
    BLINK_LED,     // Ex: Disparar o alerta visual (RGB) da campainha para testes
    SET_VALUE      // Ex: Enviar um valor específico (se necessário no futuro)
}