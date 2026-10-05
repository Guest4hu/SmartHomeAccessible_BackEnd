package com.usjt.sistema_automatizado.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Serviço de geração, assinatura e validação criptográfica de tokens JWT (JSON Web Tokens).
 */
@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    @Value("${api.security.token.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Emite um token JWT assinado contendo o identificador do usuário como subject claim.
     *
     * @param userId identificador do usuário autenticado
     * @return representação compactada em String do token JWT
     */
    public String generateToken(Long userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Verifica a integridade criptográfica da assinatura e se o token não expirou.
     *
     * @param token token JWT em formato compacto
     * @return {@code true} se o token for autêntico e válido
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extrai o identificador de usuário armazenado no subject do token JWT.
     *
     * @param token token JWT validado
     * @return identificador do usuário (userId)
     */
    public Long extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }
}