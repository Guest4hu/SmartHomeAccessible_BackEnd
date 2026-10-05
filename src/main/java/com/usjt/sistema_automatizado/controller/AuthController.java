package com.usjt.sistema_automatizado.controller;

import com.usjt.sistema_automatizado.dto.request.LoginRequest;
import com.usjt.sistema_automatizado.dto.request.RegisterRequest;
import com.usjt.sistema_automatizado.dto.response.LoginResponse;
import com.usjt.sistema_automatizado.security.CustomUserDetails;
import com.usjt.sistema_automatizado.security.TokenService;
import com.usjt.sistema_automatizado.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 1. Cria um token temporário com as credenciais que vieram do Postman
        var usernamePassword = new UsernamePasswordAuthenticationToken(request.email(), request.password());

        // 2. O Spring Security vai ao CustomUserDetailsService, busca o utilizador, encripta a senha digitada e compara com a do banco
        var auth = authenticationManager.authenticate(usernamePassword);

        // 3. Se passou pela linha de cima, a senha está correta! Extraímos o nosso CustomUserDetails
        var userDetails = (CustomUserDetails) auth.getPrincipal();

        // 4. Geramos o Token JWT real com o ID do utilizador
        String token = tokenService.generateToken(userDetails.getUser().getId());

        return ResponseEntity.ok(new LoginResponse(token, "Bearer"));
    }
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        // Retorna 201 Created vazio, indicando sucesso.
        // O utilizador deve fazer login de seguida para obter o token.
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}