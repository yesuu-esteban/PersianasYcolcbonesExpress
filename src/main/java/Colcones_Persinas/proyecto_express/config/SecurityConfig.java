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
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()

                // Gestión de pedidos de tienda (crear, editar, eliminar):
                // SOLO vendedores y admin general — va ANTES del matcher general de /tienda/**
                .requestMatchers("/tienda/nuevo", "/tienda/guardar", "/tienda/editar/**", "/tienda/eliminar/**")
                    .hasAnyRole("TIENDA", "ADMIN")

                // Resto de tienda (listado, ver detalle, cambiar estado):
                // vendedores, admin de tienda (solo lectura/estado), y admin general
                .requestMatchers("/tienda/**").hasAnyRole("TIENDA", "TIENDA_ADMIN", "ADMIN")

                // Fábrica: FABRICA y ADMIN
                .requestMatchers("/taller/**", "/inventario/**", "/reportes/**").hasAnyRole("FABRICA", "ADMIN")

                .anyRequest().authenticated()
            )
            .formLogin(login -> login
                .successHandler((request, response, authentication) -> {
                    boolean esAdmin = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    boolean esFabrica = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_FABRICA"));

                    if (esAdmin) {
                        response.sendRedirect("/taller/pedidos");
                    } else if (esFabrica) {
                        response.sendRedirect("/taller/pedidos");
                    } else {
                        response.sendRedirect("/tienda/listado");
                    }
                })
            );
        return http.build();
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

        // Administrador de tienda — solo ve y cambia estados, no crea/edita/elimina pedidos
        UserDetails adminTienda = User.builder()
            .username("admin_tienda")
            .password(passwordEncoder().encode("123456"))
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