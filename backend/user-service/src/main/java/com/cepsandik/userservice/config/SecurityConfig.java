package com.cepsandik.userservice.config;

import com.cepsandik.userservice.security.JwtAuthFilter;
import com.cepsandik.userservice.security.JwtAuthenticationEntryPoint;
import com.cepsandik.userservice.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;
        private final RateLimitFilter rateLimitFilter;
        private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint; // Inject et

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http.csrf(AbstractHttpConfigurer::disable);
                http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
                http.authorizeHttpRequests(auth -> auth
                                .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/swagger-resources/**",
                                                "/webjars/**")
                                .permitAll()
                                .requestMatchers(
                                                "/actuator/**",
                                                "/internal/**")
                                .permitAll()
                                .requestMatchers(
                                                "/api/v1/auth/register",
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/login/2fa",
                                                "/api/v1/auth/activate",
                                                "/api/v1/auth/refresh",
                                                "/api/v1/auth/verify/**",
                                                "/api/v1/auth/reset-password",
                                                "/api/v1/auth/forgot-password",
                                                "/api/v1/auth/resend-verification",
                                                "/api/v1/users/confirm-email-change/**")
                                .permitAll()
                                .anyRequest().authenticated());
                http.cors(cors -> cors.configurationSource(request -> {
                        // Faz 3.13 — CORS env-driven. "*" + allowCredentials(true)
                        // sömürülebilir kombinasyondu; APP_CORS_ALLOWED_ORIGINS
                        // (virgülle) ile kısıtla. Set edilmezse cross-origin
                        // KAPALI (en güvenli; gateway üzerinden same-origin çalışır).
                        var corsConfig = new org.springframework.web.cors.CorsConfiguration();
                        String originsEnv = System.getenv("APP_CORS_ALLOWED_ORIGINS");
                        java.util.List<String> origins = (originsEnv == null || originsEnv.isBlank())
                                ? java.util.List.of()
                                : java.util.Arrays.stream(originsEnv.split(","))
                                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                        if (origins.contains("*")) {
                                // Wildcard yalnızca credentials KAPALI iken güvenli.
                                corsConfig.setAllowedOriginPatterns(List.of("*"));
                                corsConfig.setAllowCredentials(false);
                        } else {
                                corsConfig.setAllowedOrigins(origins);
                                corsConfig.setAllowCredentials(true);
                        }
                        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                        corsConfig.setAllowedHeaders(List.of("*"));
                        return corsConfig;
                }));
                http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
                http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
                http.exceptionHandling(exception -> exception
                                .authenticationEntryPoint(jwtAuthenticationEntryPoint));
                http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
                return http.build();
        }
}
