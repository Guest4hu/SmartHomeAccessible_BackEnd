package com.usjt.sistema_automatizado.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private SecurityFilter securityFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_DeveAutenticar_QuandoBearerTokenValido() throws Exception {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer token-jwt-valido");
        when(tokenService.isTokenValid("token-jwt-valido")).thenReturn(true);
        when(tokenService.extractUserId("token-jwt-valido")).thenReturn(10L);

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());
        when(userDetailsService.loadUserById(10L)).thenReturn(userDetails);

        // Act
        securityFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(10L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_DeveAutenticarPorQueryParam_ApenasNaRotaSseStream() throws Exception {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getParameter("token")).thenReturn("token-stream-valido");
        when(request.getRequestURI()).thenReturn("/api/v1/notifications/stream");
        when(tokenService.isTokenValid("token-stream-valido")).thenReturn(true);
        when(tokenService.extractUserId("token-stream-valido")).thenReturn(20L);

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());
        when(userDetailsService.loadUserById(20L)).thenReturn(userDetails);

        // Act
        securityFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(20L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_NaoDeveAutenticarPorQueryParam_EmOutrasRotasRest() throws Exception {
        // Arrange (tentativa de passar ?token= em rota rest como /homes)
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getParameter("token")).thenReturn("token-valido-mas-em-url-rest");
        when(request.getRequestURI()).thenReturn("/api/v1/homes");

        // Act
        securityFilter.doFilterInternal(request, response, filterChain);

        // Assert (Token não deve ser recuperado em query string fora do stream)
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenService, never()).isTokenValid(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
