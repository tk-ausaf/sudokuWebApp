package com.ausaf.sudoku.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT-based security: registers {@link JwtAuthenticationFilter} (real users) and
 * {@link GuestSessionFilter} (anonymous sessions) ahead of the standard authentication filter,
 * and declares which endpoints are public vs. require a real authenticated user.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private GuestSessionFilter guestSessionFilter;

    /** Declares the filter chain: stateless sessions, guest/JWT filters, and the public/authenticated endpoint rules. */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/users/addUser").permitAll()
                .requestMatchers("/users/signIn").permitAll()
                .requestMatchers("/", "/index.html", "/static/**").permitAll()
                .requestMatchers("/users").authenticated()
                // Sudoku play/resume/leaderboard endpoints are guest-allowed at the security
                // layer; SudokuService still requires *some* identity (guest-or-real) and
                // resolves/authorizes ownership itself. These specific rules must stay ordered
                // before any broader /sudoku/** rule, since Spring Security matches first-hit.
                .requestMatchers(HttpMethod.GET, "/sudoku/puzzle").permitAll()
                .requestMatchers(HttpMethod.POST, "/sudoku/submit").permitAll()
                .requestMatchers(HttpMethod.GET, "/sudoku/attempts", "/sudoku/attempts/**").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/sudoku/attempts/*/grid").permitAll()
                .requestMatchers(HttpMethod.GET, "/sudoku/leaderboard").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                .anyRequest().permitAll()
            )
            // JwtAuthenticationFilter must be registered (and thus have a chain position) before
            // GuestSessionFilter can be placed "before" it by class reference - order matters here.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(guestSessionFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
