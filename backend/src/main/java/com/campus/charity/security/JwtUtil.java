package com.campus.charity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。
 */
@Slf4j
@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(LoginUser loginUser) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + jwtProperties.getExpireMinutes() * 60L * 1000L);
        return Jwts.builder()
                .setSubject(loginUser.getAccount())
                .claim("userId", loginUser.getUserId())
                .claim("role", loginUser.getRole())
                .claim("realName", loginUser.getRealName())
                .claim("identityNo", loginUser.getIdentityNo())
                .setIssuedAt(now)
                .setExpiration(expire)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public LoginUser toLoginUser(Claims claims) {
        LoginUser user = new LoginUser();
        Object userId = claims.get("userId");
        if (userId instanceof Number) {
            user.setUserId(((Number) userId).longValue());
        } else if (userId != null) {
            user.setUserId(Long.parseLong(String.valueOf(userId)));
        }
        user.setAccount(claims.getSubject());
        user.setRole((String) claims.get("role"));
        user.setRealName((String) claims.get("realName"));
        user.setIdentityNo((String) claims.get("identityNo"));
        return user;
    }

    public String resolveToken(String headerValue) {
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return null;
        }
        String prefix = jwtProperties.getPrefix();
        if (prefix != null && !prefix.isEmpty() && headerValue.startsWith(prefix)) {
            return headerValue.substring(prefix.length()).trim();
        }
        return headerValue.trim();
    }
}
