package com.example.helloworld.auth.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final long accessTokenMillis;
    private final long refreshTokenMillis;

    public JwtProvider(
            @Value("${jwt.access.secret}") String accessSecretB64,
            @Value("${jwt.access.expire}") long accessExpire,
            @Value("${jwt.refresh.secret}") String refreshSecretB64,
            @Value("${jwt.refresh.expire}") long refreshExpire
    ) {
        this.accessKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessSecretB64));
        this.refreshKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(refreshSecretB64));
        this.accessTokenMillis = accessExpire;
        this.refreshTokenMillis = refreshExpire;
    }

    public String issueAccessToken(Long memberId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(String.valueOf(memberId))                  // 0.11 API
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + accessTokenMillis))
                .signWith(accessKey, SignatureAlgorithm.HS256)         // 알고리즘 명시
                .compact();
    }

    public String issueRefreshToken(Long memberId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(String.valueOf(memberId))                  // 0.11 API
                .claim("tokenType", "refresh")
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + refreshTokenMillis))
                .signWith(refreshKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long parseAccessSubject(String token) {
        String sub = Jwts.parserBuilder()                              // 0.11 API
                .setSigningKey(accessKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
        return Long.parseLong(sub);
    }

    public Long parseRefreshSubject(String token) {
        String sub = Jwts.parserBuilder()
                .setSigningKey(refreshKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
        return Long.parseLong(sub);
    }
}
