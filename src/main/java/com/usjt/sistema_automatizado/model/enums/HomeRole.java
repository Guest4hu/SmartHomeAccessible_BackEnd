package com.usjt.sistema_automatizado.model.enums;

/**
 * Papel de controle de acesso do membro em relação a uma residência específica.
 *
 * <ul>
 *   <li>{@link #ADMIN}: Criador/administrador da casa; possui permissões para cadastrar dispositivos,
 *       alterar limiares de automação e convidar novos membros.</li>
 *   <li>{@link #FAMILY}: Morador da casa; possui permissões para visualizar leituras, receber alertas
 *       em tempo real, confirmar visualização de campainha e acionar comandos manuais.</li>
 * </ul>
 */
public enum HomeRole {
    ADMIN,
    FAMILY
}