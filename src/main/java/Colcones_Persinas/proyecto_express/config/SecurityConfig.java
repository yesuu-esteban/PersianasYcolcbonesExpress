package Colcones_Persinas.proyecto_express.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity // habilita @PreAuthorize en los controladores
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/login", "/login-jwt").permitAll()

                // Gestión de pedidos de tienda (crear, editar, eliminar):
                // SOLO vendedores y admin general — va ANTES del matcher general de /tienda/**
                .requestMatchers("/tienda/nuevo", "/tienda/guardar", "/tienda/editar/**", "/tienda/eliminar/**")
                    .hasAnyRole("TIENDA", "ADMIN")

                // Resto de tienda (listado, ver detalle, imprimir, abonar, cambiar estado):
                // vendedores, admin de tienda (solo lectura/estado/abono), y admin general
                .requestMatchers("/tienda/**").hasAnyRole("TIENDA", "TIENDA_ADMIN", "ADMIN")

                // Fábrica: FABRICA y ADMIN
                .requestMatchers("/taller/**", "/inventario/**", "/reportes/**").hasAnyRole("FABRICA", "ADMIN")

                .anyRequest().authenticated()
            )
            .formLogin(login -> login.disable())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) ->
                response.sendRedirect("/login")
            ));
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(InMemoryUserDetailsManager uds) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails jefeFabrica = User.builder()
            .username("jefe")
            .password(passwordEncoder().encode("123456"))
            .roles("FABRICA")
            .build();

        UserDetails vendedor = User.builder()
            .username("vendedor1")
            .password(passwordEncoder().encode("123456"))
            .roles("TIENDA")
            .build();

        // Administrador de tienda — solo ve, abona y cambia estados; no crea/edita/elimina pedidos
        UserDetails adminTienda = User.builder()
            .username("Tienda")
            .password(passwordEncoder().encode("express"))
            .roles("TIENDA_ADMIN")
            .build();

        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("123456"))
            .roles("ADMIN")
            .build();

        return new InMemoryUserDetailsManager(jefeFabrica, vendedor, adminTienda, admin);
    }
}