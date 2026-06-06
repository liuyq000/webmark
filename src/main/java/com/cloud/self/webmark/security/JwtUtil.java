package com.cloud.self.webmark.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtil(String secret, long accessExpirationMs, long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /** 生成 access token，30 分钟有效 */
    public String generateAccessToken(String username, String role) {
        return buildToken(username, role, "access", accessExpirationMs);
    }

    /** 生成 refresh token，7 天有效 */
    public String generateRefreshToken(String username, String role) {
        return buildToken(username, role, "refresh", refreshExpirationMs);
    }

    private String buildToken(String username, String role, String type, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    public String getTokenType(String token) {
        try {
            return parseToken(token).get("type", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
