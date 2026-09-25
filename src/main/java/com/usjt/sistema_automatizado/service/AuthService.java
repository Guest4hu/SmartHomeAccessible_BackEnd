package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.RegisterRequest;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // Injetamos o BCrypt que configurou no SecurityConfig

    public void register(RegisterRequest request) {
        // 1. Verifica se o e-mail já existe na base de dados
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("Este e-mail já está registado.");
        }

        // 2. Cria a entidade AppUser
        AppUser newUser = new AppUser();
        newUser.setName(request.name());
        newUser.setEmail(request.email());

        // 3. Faz o hash da palavra-passe antes de guardar!
        newUser.setPasswordHash(passwordEncoder.encode(request.password()));

        // O campo active=true e createdAt já são tratados pela entidade automaticamente

        // 4. Guarda na base de dados
        userRepository.save(newUser);
    }
}