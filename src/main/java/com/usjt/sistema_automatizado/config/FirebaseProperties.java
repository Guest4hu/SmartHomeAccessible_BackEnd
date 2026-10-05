package com.usjt.sistema_automatizado.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de configuração do Firebase Cloud Messaging (FCM).
 *
 * <p>Permite alternar entre despacho em nuvem real e modo Mock/Dry-Run estruturado em log
 * para ambientes locais, CI/CD e suítes de testes automatizados.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.firebase")
public class FirebaseProperties {

    /**
     * Habilita a integração com o Firebase. Padrão: false.
     */
    private boolean enabled = false;

    /**
     * Quando true, simula o envio de mensagens registrando logs estruturados
     * sem realizar chamadas de rede para o Google. Padrão: true.
     */
    private boolean dryRun = true;

    /**
     * Caminho no sistema de arquivos para o arquivo de credenciais da conta de serviço (serviceAccountKey.json).
     */
    private String credentialsPath;
}
