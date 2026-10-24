package com.workbench.workshop.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "akmsdkmasfnjsgknakmsdkmasdkmsadfkmdsakmdkmsadsa";
    private final JwtService service = new JwtService(new JwtProperties(SECRET));
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private String staff() {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject("admin@workbench.dev")
                .claim("roles", List.of("ADMIN", "TECHNICIAN"))
                .issuedAt(new Date(now)).expiration(new Date(now + 60_000))
                .signWith(key).compact();
    }

    private String client() {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject("52998224725")
                .claim("clientId", 1L).claim("roles", List.of("CLIENT"))
                .issuedAt(new Date(now)).expiration(new Date(now + 60_000))
                .signWith(key).compact();
    }

    @Test
    void parsesStaffTokenAndReadsRoles() {
        Claims claims = service.tryParseClaims(staff()).orElseThrow();
        assertFalse(service.isClientToken(claims));
        assertTrue(service.extractRoles(claims).contains("ADMIN"));
        assertFalse(service.isExpired(claims));
    }

    @Test
    void detectsClientToken() {
        Claims claims = service.tryParseClaims(client()).orElseThrow();
        assertTrue(service.isClientToken(claims));
        assertEquals(List.of("CLIENT"), service.extractRoles(claims));
    }

    @Test
    void invalidSignatureReturnsEmpty() {
        assertTrue(service.tryParseClaims("not-a-jwt").isEmpty());
    }

    @Test
    void extractRolesEmptyWhenMissing() {
        String noRoles = Jwts.builder().subject("x")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
        Claims claims = service.tryParseClaims(noRoles).orElseThrow();
        assertTrue(service.extractRoles(claims).isEmpty());
    }

    @Test
    void rejectsExpiredToken() {
        long now = System.currentTimeMillis();
        String expired = Jwts.builder().subject("x")
                .issuedAt(new Date(now - 10_000)).expiration(new Date(now - 5_000))
                .signWith(key).compact();
        assertTrue(service.tryParseClaims(expired).isEmpty());
    }
}
