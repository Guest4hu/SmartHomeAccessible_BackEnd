package com.usjt.sistema_automatizado;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@IntegrationComponentScan
@EnableScheduling
public class SistemaAutomatizadoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SistemaAutomatizadoApplication.class, args);
	}

}
