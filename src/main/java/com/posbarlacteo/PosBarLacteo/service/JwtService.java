package com.posbarlacteo.PosBarLacteo.service; // ✨ Cambio de .security a .service
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    // CLAVE SECRETA DE FIRMA: En producción, guárdala en una variable de entorno de tu servidor o en AWS
    private static final String SECRET = "EstaEsUnaClaveSecretaMuyLargaYPruebaParaPOSBarLacteo2026SuperSegura";
    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    public String generarToken(String usuario, String rol) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("rol", rol); // Sellamos el rol aquí dentro del token

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(usuario)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                // Token con expiración a 10 horas
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validarToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Claims obtenerClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }
}