package com.workbench.workshop.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Segurança do Execution Service (defesa em profundidade: cada serviço valida o JWT).
 * Catálogo de produtos e estoque são operações internas da oficina, então todas as
 * rotas exigem token de staff (ADMIN/TECHNICIAN). Públicas apenas health e Swagger.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) { this.jwtFilter = jwtFilter; }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .frameOptions(fo -> fo.deny())
                        .contentTypeOptions(c -> {}))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().hasAnyRole("ADMIN", "TECHNICIAN"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exAuth) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, exAuth.getMessage())))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
