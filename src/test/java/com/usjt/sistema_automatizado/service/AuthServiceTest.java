package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.RegisterRequest;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Habilita o Mockito para gerir os "dublês" nesta classe
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // 1. Criamos mocks (dublês) das dependências que o AuthService precisa
    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // 2. Injetamos os mocks na classe que vamos testar de verdade (AuthService)
    @InjectMocks
    private AuthService authService;

    @Test
    void register_DeveLancarExcecao_QuandoEmailJaExistir() {
        // Arrange (Preparar)
        RegisterRequest request = new RegisterRequest("João", "joao@email.com", "senha123");

        // Ensinamos o mock: "Quando alguém procurar por joao@email.com, devolva um utilizador existente"
        when(userRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(new AppUser()));

        // Act & Assert (Agir e Verificar)
        // Verificamos se o método lança a exceção esperada
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(request)
        );

        assertEquals("Este e-mail já está registado.", exception.getMessage());

        // Verificamos que o repository.save NUNCA foi chamado, afinal, deu erro antes
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_DeveCriarUsuarioComSenhaHasheada_QuandoDadosForemValidos() {
        // Arrange (Preparar)
        RegisterRequest request = new RegisterRequest("Maria", "maria@email.com", "senha123");

        // Ensinamos os mocks: "O email não existe" e "A senha hasheada será XYZ"
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("senhaHasheadaXYZ");

        // Act (Agir)
        authService.register(request);

        // Assert (Verificar)
        // Usamos o ArgumentCaptor para "capturar" a entidade AppUser exata que o AuthService tentou guardar no banco
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        AppUser savedUser = userCaptor.getValue();

        // Verificamos se o service montou o objeto corretamente
        assertEquals("Maria", savedUser.getName());
        assertEquals("maria@email.com", savedUser.getEmail());
        assertEquals("senhaHasheadaXYZ", savedUser.getPasswordHash()); // Garante que a senha não foi guardada em texto limpo!
    }
}