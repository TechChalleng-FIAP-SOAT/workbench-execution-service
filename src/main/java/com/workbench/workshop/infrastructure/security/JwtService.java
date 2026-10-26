package com.workbench.workshop.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Validação de JWT no Execution Service. Não emite tokens — apenas verifica a
 * assinatura HS256 com o JWT_SECRET compartilhado e lê as claims. Mesmo padrão do
 * OS Service.
 */
@Service
@Slf4j
public class JwtService {

    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public Optional<Claims> tryParseClaims(String token) {
        try {
            return Optional.of(getClaims(token));
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isClientToken(Claims claims) {
        return claims.get("clientId") != null;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public boolean isExpired(Claims claims) {
        return claims.getExpiration() != null && claims.getExpiration().before(new Date());
    }

    private Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
