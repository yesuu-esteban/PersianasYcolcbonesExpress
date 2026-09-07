package Colcones_Persinas.proyecto_express.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
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
                // El portal es público: cualquiera lo ve, pero las tarjetas
                // aparecen bloqueadas hasta iniciar sesión (lo decide sec:authorize en Vista.html).
                // "/recuperar-password/**" también es público: es precisamente el flujo
                // para gente que NO puede iniciar sesión porque olvidó su contraseña.
                .requestMatchers("/css/**", "/js/**", "/images/**", "/login", "/login-jwt", "/", "/portal").permitAll()

                // Gestión de pedidos de tienda
                .requestMatchers("/tienda/nuevo", "/tienda/guardar", "/tienda/editar/**", "/tienda/eliminar/**")
                    .hasAnyRole("TIENDA", "ADMIN")

                // Resto de tienda
                .requestMatchers("/tienda/**").hasAnyRole("TIENDA", "TIENDA_ADMIN", "ADMIN")

                // Fábrica
                .requestMatchers("/taller/**", "/inventario/**", "/reportes/**").hasAnyRole("FABRICA", "ADMIN")

                // Cada usuario logueado (cualquier rol) gestiona su propia cuenta
                .requestMatchers("/mi-cuenta/**").authenticated()

                // Administración de usuarios: solo ADMIN
                .requestMatchers("/usuarios/**").hasRole("ADMIN")

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

    // Ahora se conecta a UsuarioDetailsService (busca en la tabla `usuario`)
    // en vez del InMemoryUserDetailsManager que tenía los usuarios hardcodeados.
    @Bean
    public DaoAuthenticationProvider authenticationProvider(UsuarioDetailsService usuarioDetailsService) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(usuarioDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}