package com.example.helloworld.userserver.auth.application;

import com.example.helloworld.userserver.auth.application.command.LoginCommand;
import com.example.helloworld.userserver.auth.application.result.LoginResult;
import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.auth.presentation.request.LogoutRequest;
import com.example.helloworld.userserver.auth.token.*;
import com.example.helloworld.userserver.exception.HelloWordException;
import com.example.helloworld.userserver.exception.code.AuthErrorCode;
import com.example.helloworld.userserver.member.entity.Member;
import com.example.helloworld.userserver.member.persistence.MemberRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;

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
            member = createMemberWithRetry(googleEmail, googleName); // <-- 새 헬퍼로 위임
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
        if (saved.isRevoked() || saved.getExpiresAt().isBefore(Instant.now())) {
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
                        .expiresAt(Instant.ofEpochMilli(System.currentTimeMillis() + refreshMillis))
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

    @Transactional
    public void withdraw(Long memberId) {
        // 1) RT 모두 무효화(선택: FK CASCADE면 생략 가능)
        refreshTokenRepository.revokeAllByMemberId(memberId);

        // 2) 하드 삭제
        if (!memberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        memberRepository.deleteById(memberId); // 실제 DELETE
    }

    private Member createMemberWithRetry(String googleEmail, String googleName) {
        for (int i = 0; i < 5; i++) {
            try {
                Member toSave = Member.builder()
                        .googleEmail(googleEmail)
                        .nickname(generateNickname())
                        .build();
                return memberRepository.save(toSave); // 성공 시 즉시 반환
            } catch (DataIntegrityViolationException ex) {
                // 1) 이메일 경합: 누군가 먼저 가입
                Member existing = memberRepository.findByGoogleEmail(googleEmail).orElse(null);
                if (existing != null) return existing;

                // 2) 닉네임 UNIQUE 충돌 등 → 닉네임 재생성 후 재시도
                if (i == 4) throw ex; // 마지막에도 실패면 그대로 던져 트랜잭션 롤백
            }
        }
        throw new IllegalStateException("회원 생성 재시도 초과");
    }
}
