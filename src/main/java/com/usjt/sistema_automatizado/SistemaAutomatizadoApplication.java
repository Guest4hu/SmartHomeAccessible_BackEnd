package com.usjt.sistema_automatizado;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.integration.annotation.IntegrationComponentScan;

@SpringBootApplication
@IntegrationComponentScan
public class SistemaAutomatizadoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SistemaAutomatizadoApplication.class, args);
	}

}
