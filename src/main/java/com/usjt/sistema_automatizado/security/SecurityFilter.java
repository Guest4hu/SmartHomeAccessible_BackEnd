package com.usjt.sistema_automatizado.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro interceptor de segurança executado uma única vez por requisição HTTP.
 *
 * <p><b>Extração do Token JWT:</b> Suporta a recuperação do token via cabeçalho convencional
 * ({@code Authorization: Bearer <token>}) e via parâmetro de consulta ({@code ?token=...}).
 * O suporte ao parâmetro de consulta é uma decisão arquitetural mandatória para compatibilidade
 * com a API nativa {@code EventSource} dos navegadores (utilizada no streaming de alertas SSE),
 * que não permite a customização de cabeçalhos HTTP na inicialização da conexão.</p>
 */
@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = recoverToken(request);

        if (token != null && tokenService.isTokenValid(token)) {
            // Extrai o ID do token e busca as credenciais
            Long userId = tokenService.extractUserId(token);
            UserDetails user = userDetailsService.loadUserById(userId);

            // Autentica a requisição para o Spring
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.replace("Bearer ", "");
        }
        String tokenParam = request.getParameter("token");
        // O suporte a query param é exclusivo para EventSource SSE (que não permite headers customizados)
        if (tokenParam != null && !tokenParam.isBlank() && "/api/v1/notifications/stream".equals(request.getRequestURI())) {
            return tokenParam;
        }
        return null;
    }
}