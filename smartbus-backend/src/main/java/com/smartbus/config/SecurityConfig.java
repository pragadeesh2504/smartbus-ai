package com.smartbus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.security.CustomUserDetailsService;
import com.smartbus.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> apiResponse = ApiResponse.error("Authentication required");
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> apiResponse = ApiResponse.error("Access denied");
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .contentTypeOptions(HeadersConfigurer.ContentTypeOptionsConfig::disable) // spring adds X-Content-Type-Options by default
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://maps.googleapis.com; " +
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    "img-src 'self' data: https://*.tile.openstreetmap.org https://*.openstreetmap.org https://*.basemaps.cartocdn.com https://*.cartocdn.com https://maps.googleapis.com https://maps.gstatic.com; " +
                    "connect-src 'self' ws: wss: http: https: https://maps.googleapis.com; " +
                    "font-src 'self' data: https://fonts.gstatic.com; " +
                    "frame-ancestors 'self';"
                ))
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/ws/live/**", "/ws/live").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/super-admin/**", "/super-admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/actuator/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/driver/**").hasRole("DRIVER")
                .requestMatchers("/student/**").hasRole("STUDENT")
                // SEC-01: Bus endpoint granular permissions
                .requestMatchers(HttpMethod.GET, "/buses/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/buses/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/buses/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/buses/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/buses/**").hasRole("ADMIN")
                // SEC-01: Route endpoint granular permissions
                .requestMatchers(HttpMethod.GET, "/routes/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/routes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/routes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/routes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/routes/**").hasRole("ADMIN")
                // SEC-01 & SEC-02: Trip endpoint granular permissions
                .requestMatchers(HttpMethod.GET, "/trips/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/trips/start").hasAnyRole("DRIVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/trips/*/pause").hasAnyRole("DRIVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/trips/*/resume").hasAnyRole("DRIVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/trips/*/end").hasAnyRole("DRIVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/trips/*/location").hasAnyRole("DRIVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/trips/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/trips/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/trips/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/trips/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            );

        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(Collections.singletonList("Authorization"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
