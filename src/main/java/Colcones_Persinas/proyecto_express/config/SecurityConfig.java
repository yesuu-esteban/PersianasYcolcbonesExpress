package Colcones_Persinas.proyecto_express.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll() // Permitir archivos estáticos
                .anyRequest().authenticated()
            )
            .formLogin(login -> login
                .defaultSuccessUrl("/taller/pedidos", true) // CORREGIDO: Ruta completa
            ); 
        return http.build();
    }
    // 1. Definimos el codificador de contraseñas
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 2. Usamos el codificador para crear el usuario
    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails user = User.builder()
            .username("persianas")
            .password(passwordEncoder().encode("123")) // Aquí se codifica la clave
            .roles("USER")
            .build();
        return new InMemoryUserDetailsManager(user);
    }
}