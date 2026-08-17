package com.aiimpacteval.apicore.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Server-side RBAC (E8-S1, ADR-0004). The UI hides; this chain denies.
 * Stateless JWT API.
 *
 * <p>
 * Route policy:
 * <ul>
 * <li>{@code /actuator/health/**} — public (probes).</li>
 * <li>{@code /api/v1/auth/**} — public (dev-token bridge; gated +
 * audited).</li>
 * <li>{@code /api/v1/audit/**} — {@code ADMIN} only (E8-S3).</li>
 * <li>{@code /api/v1/admin/**} — {@code ADMIN} only (Admin console: connector
 * health, user/role management, E1-S4/E8).</li>
 * <li>{@code /api/v1/metrics/**}, {@code /api/v1/teams/**} — analytical roles
 * only;
 * {@code IC} is denied — these aren't self-scoped, so IC has
 * {@code /api/v1/personal/**} instead (E8-S1/S2).</li>
 * <li>{@code /api/v1/personal/**} — any authenticated role. Safe to leave
 * un-role-gated: it's inherently self-scoped (a caller's own PRs/reviews
 * only), so there's no privilege to restrict.</li>
 * <li>{@code /api/v1/setup/**} — {@code ADMIN}/{@code ENG_LEADER} only
 * (onboarding
 * checklist, E1-S5).</li>
 * <li>everything else — authenticated.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/audit/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/api/v1/admin/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/api/v1/metrics/**", "/api/v1/teams/**").hasAnyRole(
                                Role.ADMIN.name(), Role.ENG_LEADER.name(),
                                Role.MANAGER.name(), Role.FINANCE_READONLY.name())
                        .requestMatchers("/api/v1/personal/**").authenticated()
                        .requestMatchers("/api/v1/setup/**")
                        .hasAnyRole(Role.ADMIN.name(), Role.ENG_LEADER.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())));
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private JwtAuthenticationConverter roleConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}