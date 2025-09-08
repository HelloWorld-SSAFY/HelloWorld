package com.example.helloworld.auth.application;

import com.example.helloworld.auth.application.command.LoginCommand;
import com.example.helloworld.auth.application.result.LoginResult;
import com.example.helloworld.auth.jwt.JwtProvider;
import com.example.helloworld.auth.presentation.request.LogoutRequest;
import com.example.helloworld.auth.token.*;
import com.example.helloworld.exception.HelloWordException;
import com.example.helloworld.exception.code.AuthErrorCode;
import com.example.helloworld.member.domain.Member;
import com.example.helloworld.member.persistence.MemberRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthService {

    private final OAuthClient oAuthClient;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh.expire}")     // ms 단위
    private long refreshMillis;

    /**
     * 로그인 + 자동 회원가입 (통합)
     * - Google idToken 검증
     * - 기존 회원 조회: googleEmail 기준
     * - 없으면 자동 생성(닉네임 = "user" + 4자리 숫자, 중복 시 재시도)
     * - JWT 발급(subject = memberId)
     */
    @Transactional
    public LoginResult login(LoginCommand command) {
        Payload payload = oAuthClient.verify(command.idToken())
                .orElseThrow(() -> new HelloWordException(AuthErrorCode.INVALID_ID_TOKEN));

        String googleEmail = payload.getEmail();
        String googleName  = (String) payload.get("name");
        if (googleEmail == null || googleEmail.isBlank()) {
            throw new HelloWordException(AuthErrorCode.INVALID_ID_TOKEN);
        }

        Member member = memberRepository.findByGoogleEmail(googleEmail).orElse(null);

        if (member == null) {
            String name = firstNonBlank(googleName, emailLocalPart(googleEmail));
            String nickname = generateNickname();

            member = Member.builder()
                    .googleEmail(googleEmail)
                    .name(name)
                    .nickname(nickname)
                    .build();

            try {
                memberRepository.save(member);
            } catch (DataIntegrityViolationException e) {
                Member existing = memberRepository.findByGoogleEmail(googleEmail).orElse(null);
                if (existing == null) throw e;
                member = existing;
            }
        }

        String accessToken  = jwtProvider.issueAccessToken(member.getId());
        String refreshToken = jwtProvider.issueRefreshToken(member.getId());

        // 만료 계산 & 저장
        Instant rtExpiry = Instant.ofEpochMilli(System.currentTimeMillis() + refreshMillis);
        RefreshToken rt = RefreshToken.builder()
                .memberId(member.getId())
                .tokenHash(TokenHashes.sha256B64(refreshToken))
                .expiresAt(rtExpiry)
                .revoked(false)
                .build();
        refreshTokenRepository.save(rt);

        return new LoginResult(member.getId(), accessToken, refreshToken);
    }

    private String generateNickname() {
        String candidate;
        do {
            int rand = ThreadLocalRandom.current().nextInt(1000, 10000);
            candidate = "user" + rand;
        } while (memberRepository.existsByNickname(candidate));
        return candidate;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c.trim();
        }
        return null;
    }

    private static String emailLocalPart(String email) {
        int idx = email.indexOf('@');
        return (idx > 0) ? email.substring(0, idx) : email;
    }

    @Transactional
    public RefreshResponse refresh(RefreshRequest req){
        String rt = req.refreshToken();
        Long memberId = jwtProvider.parseRefreshSubject(rt);

        var saved = refreshTokenRepository.findByTokenHash(TokenHashes.sha256B64(rt))
                .orElseThrow(() -> new HelloWordException(AuthErrorCode.INVALID_ID_TOKEN));
        if (saved.isRevoked() || saved.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new HelloWordException(AuthErrorCode.INVALID_ID_TOKEN);
        }

        // rotate
        saved.revoke(); // 필드 setter 추가하거나 copy-entity 저장 방식으로 처리
        String newAT = jwtProvider.issueAccessToken(memberId);
        String newRT = jwtProvider.issueRefreshToken(memberId);
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .memberId(memberId)
                        .tokenHash(TokenHashes.sha256B64(newRT))
                        .expiresAt(java.time.Instant.ofEpochMilli(System.currentTimeMillis() + refreshMillis))
                        .revoked(false)
                        .build()
        );
        return new RefreshResponse(memberId, newAT, newRT);
    }

    @Transactional
    public void logout(LogoutRequest req) {
        String rt = req.refreshToken();
        if (rt == null || rt.isBlank()) return; // 바디 비어도 조용히 종료(정보 노출 방지)
        String hash = TokenHashes.sha256B64(rt);
        refreshTokenRepository.findByTokenHash(hash)
                .ifPresent(RefreshToken::revoke); // 엔티티의 revoke() 사용
    }


}
