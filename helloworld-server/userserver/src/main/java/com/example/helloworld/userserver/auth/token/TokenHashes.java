package com.example.helloworld.userserver.auth.token;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class TokenHashes {
    private TokenHashes(){}
    public static String sha256B64(String token) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    md.digest(token.getBytes(StandardCharsets.UTF_8))  // UTF-8 명시
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
