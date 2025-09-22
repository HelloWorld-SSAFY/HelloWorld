package com.example.helloworld.userserver.auth.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    @Getter
    private final long accessTokenMillis;
    private final long refreshTokenMillis;

    public JwtProvider(
            @Value("${jwt.access.secret}") String accessSecretB64,
            @Value("${jwt.access.expire}") long accessExpire,
            @Value("${jwt.refresh.secret}") String refreshSecretB64,
            @Value("${jwt.refresh.expire}") long refreshExpire
    ) throws NoSuchAlgorithmException {
        this.accessKey  = Keys.hmacShaKeyFor(accessSecretB64.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecretB64.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMillis = accessExpire;
        this.refreshTokenMillis = refreshExpire;
        var md = java.security.MessageDigest.getInstance("SHA-256");
        String fpA = java.util.HexFormat.of().formatHex(md.digest(accessKey.getEncoded()));
        String fpR = java.util.HexFormat.of().formatHex(md.digest(refreshKey.getEncoded()));
        System.out.println("[JWT] accessKey fp=" + fpA + " refreshKey fp=" + fpR);
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

    public long getAccessTokenRemainingSeconds(String token) {
        Date exp = Jwts.parserBuilder()
                .setSigningKey(accessKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
        long nowSec = System.currentTimeMillis() / 1000;
        return Math.max(1, exp.getTime() / 1000 - nowSec);
    }

}
