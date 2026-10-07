package Colcones_Persinas.proyecto_express.config.seguridad;

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
                // El portal es público: cualquiera lo ve, pero los módulos solo aparecen
                // al iniciar sesión (lo decide sec:authorize en Vista.html).
                // "/logout" también es público: así se puede cerrar sesión aunque el token ya haya vencido.
                .requestMatchers("/css/**", "/js/**", "/images/**", "/login", "/login-jwt", "/logout", "/", "/portal").permitAll()

                // 🆕 Tienda virtual PÚBLICA (catálogo, carrito, pago y aviso de Wompi)
                .requestMatchers("/tienda", "/tienda/**").permitAll()

                // 🆕 Administración de la tienda virtual
                .requestMatchers("/tienda-admin", "/tienda-admin/**").hasAnyRole("TIENDA_ADMIN", "ADMIN")

                // Gestión de pedidos de almacén (antes "tienda" — los roles internos
                // TIENDA/TIENDA_ADMIN se conservan igual en la base de datos).
                .requestMatchers("/almacen/nuevo", "/almacen/guardar", "/almacen/editar/**", "/almacen/eliminar/**")
                    .hasAnyRole("TIENDA", "ADMIN")

                // Resto de almacén
                .requestMatchers("/almacen/**").hasAnyRole("TIENDA", "TIENDA_ADMIN", "ADMIN")

                // Módulo de Instalaciones (el jefe asigna y gestiona las tareas)
                .requestMatchers("/instalaciones", "/instalaciones/**").hasAnyRole("TIENDA_ADMIN", "ADMIN")

                // Agenda personal de cada instalador
                .requestMatchers("/mi-calendario", "/mi-calendario/**").hasRole("INSTALADOR")

                // Fábrica
                .requestMatchers("/taller/**", "/inventario/**", "/reportes/**").hasAnyRole("FABRICA", "ADMIN")

                // Cada usuario logueado (cualquier rol) gestiona su propia cuenta
                .requestMatchers("/mi-cuenta/**").authenticated()

                // Administración de usuarios: solo ADMIN
                .requestMatchers("/usuarios/**").hasRole("ADMIN")

                .requestMatchers("/recibos/**").authenticated()

                .anyRequest().authenticated()
            )
            .formLogin(login -> login.disable())
            // Se desactiva el "cerrar sesión" que trae Spring Security por defecto: atrapaba
            // /logout ANTES de llegar a LoginController y NO borraba la cookie "authToken".
            // Ahora /logout lo atiende LoginController.logout(), que sí borra todo.
            .logout(logout -> logout.disable())
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

    // Se conecta a UsuarioDetailsService (busca en la tabla `usuario`).
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