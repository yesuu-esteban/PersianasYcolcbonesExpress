package Colcones_Persinas.proyecto_express.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtService {

    // ⚠️ Cámbiala por una clave propia larga y guárdala como variable de entorno en producción.
    private static final String SECRET_BASE64 =
        "Y2FtYmlhLWVzdGEtY2xhdmUtcG9yLXVuYS1sYXJnYS15LXNlZ3VyYS1kZS1taW5pbW8tMzItYnl0ZXM=";

    private static final long EXPIRACION_MS = 8 * 60 * 60 * 1000; // 8 horas

    private SecretKey getKey() {
        byte[] bytes = Decoders.BASE64.decode(SECRET_BASE64);
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generarToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());

        Date ahora = new Date();
        Date expira = new Date(ahora.getTime() + EXPIRACION_MS);

        return Jwts.builder()
            .subject(userDetails.getUsername())
            .claim("roles", roles)
            .issuedAt(ahora)
            .expiration(expira)
            .signWith(getKey())
            .compact();
    }

    public Claims validarYObtenerClaims(String token) {
        return Jwts.parser()
            .verifyWith(getKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean esTokenValido(String token) {
        try {
            Claims claims = validarYObtenerClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}