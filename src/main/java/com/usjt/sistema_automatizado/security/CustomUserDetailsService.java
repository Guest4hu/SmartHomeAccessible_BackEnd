package com.usjt.sistema_automatizado.security;

import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Vai à base de dados procurar pelo email
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilizador não encontrado com o email: " + email));

        // Embrulha na nossa classe de segurança e devolve ao Spring Security
        return new CustomUserDetails(user);
    }
    // Método extra para ser usado pelo nosso SecurityFilter (muito mais rápido via PK)
    public UserDetails loadUserById(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilizador não encontrado com o ID: " + id));
        return new CustomUserDetails(user);
    }
}