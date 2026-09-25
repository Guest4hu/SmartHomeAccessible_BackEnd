package com.usjt.sistema_automatizado;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SistemaAutomatizadoApplication {

	public static void main(String[] args) {
		System.out.println("Senha");
		System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("123456"));
		SpringApplication.run(SistemaAutomatizadoApplication.class, args);
	}

}
