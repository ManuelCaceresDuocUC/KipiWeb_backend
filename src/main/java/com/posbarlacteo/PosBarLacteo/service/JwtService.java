package com.posbarlacteo.PosBarLacteo.service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final Key key;
    private final long expiracionMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-hours:10}") long horas) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET no está definido o es demasiado corto (mínimo 32 caracteres). "
                            + "Configúralo como variable de entorno en Render.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiracionMs = horas * 60L * 60L * 1000L;
    }

    /** Genera un token que lleva la identidad completa del usuario (empresa, sucursal y rol). */
    public String generarToken(AuthPrincipal p) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", p.usuarioId());
        claims.put("eid", p.empresaId());
        if (p.sucursalId() != null) {
            claims.put("sid", p.sucursalId());
        }
        claims.put("rol", p.rol());

        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(p.usuario())
                .setIssuedAt(new Date(ahora))
                .setExpiration(new Date(ahora + expiracionMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Valida firma y expiración y devuelve la identidad, o null si el token no sirve
     * (incluye tokens antiguos sin empresa: obligan a iniciar sesión de nuevo).
     */
    public AuthPrincipal leerPrincipal(String token) {
        try {
            Claims c = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            Number uid = c.get("uid", Number.class);
            Number eid = c.get("eid", Number.class);
            Number sid = c.get("sid", Number.class);
            String rol = c.get("rol", String.class);
            if (uid == null || eid == null || rol == null) {
                return null;
            }
            return new AuthPrincipal(
                    uid.longValue(),
                    c.getSubject(),
                    eid.longValue(),
                    sid != null ? sid.longValue() : null,
                    rol);
        } catch (Exception e) {
            return null;
        }
    }
}
