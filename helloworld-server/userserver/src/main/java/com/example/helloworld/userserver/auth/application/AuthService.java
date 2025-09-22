package com.example.helloworld.userserver.auth.application;

import com.example.helloworld.userserver.auth.application.command.LoginCommand;
import com.example.helloworld.userserver.auth.application.result.LoginResult;
import com.example.helloworld.userserver.auth.jwt.JwtProvider;
import com.example.helloworld.userserver.auth.presentation.request.LogoutRequest;
import com.example.helloworld.userserver.auth.token.RefreshRequest;
import com.example.helloworld.userserver.auth.token.RefreshResponse;
import com.example.helloworld.userserver.auth.token.RefreshToken;
import com.example.helloworld.userserver.auth.token.RefreshTokenRepository;
import com.example.helloworld.userserver.auth.token.TokenHashes;
import com.example.helloworld.userserver.exception.HelloWordException;
import com.example.helloworld.userserver.exception.code.AuthErrorCode;
import com.example.helloworld.userserver.member.entity.Member;
import com.example.helloworld.userserver.member.persistence.CoupleRepository;
import com.example.helloworld.userserver.member.persistence.MemberRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import io.jsonwebtoken.Jwts;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AuthService {

    private final OAuthClient oAuthClient;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenCacheService tokenCacheService;
    private final CoupleRepository coupleRepository;

    @Value("${jwt.refresh.expire}")     // ms 단위
    private long refreshMillis;

    private record CoupleInfo(Long coupleId, String role) {}

    private CoupleInfo resolveCoupleInfo(Long memberId) {
        return coupleRepository.findByUserA_IdOrUserB_Id(memberId, memberId)
                .map(c -> {
                    String role = (c.getUserA() != null && c.getUserA().getId().equals(memberId)) ? "A"
                            : (c.getUserB() != null && c.getUserB().getId().equals(memberId)) ? "B"
                            : null;
                    return new CoupleInfo(c.getId(), role);
                })
                .orElse(new CoupleInfo(null, null));
    }

    /**
     * 로그인 + 자동 회원가입 (통합)
     */
    @Transactional
    public LoginResult login(LoginCommand command) {
        var payload = oAuthClient.verify(command.idToken())
                .orElseThrow(() -> new HelloWordException(AuthErrorCode.INVALID_ID_TOKEN));

        String googleEmail = payload.getEmail();
        String googleName  = (String) payload.get("name");
        if (googleEmail == null || googleEmail.isBlank()) {
            throw new HelloWordException(AuthErrorCode.INVALID_ID_TOKEN);
        }

        Member member = memberRepository.findByGoogleEmail(googleEmail).orElse(null);
        if (member == null) {
            member = createMemberWithRetry(googleEmail, googleName);
        }

        // 커플/역할 해석
        CoupleInfo ci = resolveCoupleInfo(member.getId());

        String accessToken  = jwtProvider.issueAccessToken(member.getId());
        String refreshToken = jwtProvider.issueRefreshToken(member.getId());

        // RT 저장
        Instant rtExpiry = Instant.ofEpochMilli(System.currentTimeMillis() + refreshMillis);
        RefreshToken rt = RefreshToken.builder()
                .memberId(member.getId())
                .tokenHash(TokenHashes.sha256B64(refreshToken))
                .expiresAt(rtExpiry)
                .revoked(false)
                .build();
        refreshTokenRepository.save(rt);

        // AT → Redis (coupleId/role 포함)
        try {
            long accessExpMs = System.currentTimeMillis() + jwtProvider.getAccessTokenMillis();
            tokenCacheService.registerAccessToken(
                    accessToken, member.getId(), ci.coupleId(), ci.role(), accessExpMs
            );
        } catch (Exception e) {
            log.warn("Failed to register access token in cache for memberId={}: {}", member.getId(), e.getMessage());
        }

        String gender = (member.getGender() == null) ? null : member.getGender().toString();
        return new LoginResult(member.getId(), accessToken, refreshToken, gender);
    }


    // -----------------------
    // RTR 적용된 refresh
    // -----------------------
    @Transactional
    public RefreshResponse refresh(RefreshRequest req) {
        String incomingRt = req.refreshToken();
        if (incomingRt == null || incomingRt.isBlank()) {
            throw new HelloWordException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String hash = TokenHashes.sha256B64(incomingRt);
        int updated = refreshTokenRepository.revokeIfNotRevoked(hash);

        if (updated == 1) {
            Long memberId;
            try { memberId = jwtProvider.parseRefreshSubject(incomingRt); }
            catch (Exception e) { throw new HelloWordException(AuthErrorCode.INVALID_REFRESH_TOKEN); }

            String newAT = jwtProvider.issueAccessToken(memberId);
            String newRT = jwtProvider.issueRefreshToken(memberId);

            RefreshToken newEntity = RefreshToken.builder()
                    .memberId(memberId)
                    .tokenHash(TokenHashes.sha256B64(newRT))
                    .expiresAt(Instant.ofEpochMilli(System.currentTimeMillis() + refreshMillis))
                    .revoked(false)
                    .build();
            refreshTokenRepository.save(newEntity);

            // 커플/역할 해석
            CoupleInfo ci = resolveCoupleInfo(memberId);

            try {
                long accessExpMs = System.currentTimeMillis() + jwtProvider.getAccessTokenMillis();
                tokenCacheService.registerAccessToken(newAT, memberId, ci.coupleId(), ci.role(), accessExpMs);
            } catch (Exception e) {
                log.warn("token cache registration failed for memberId={}: {}", memberId, e.getMessage());
            }

            return new RefreshResponse(memberId, newAT, newRT);
        } else {
            var maybe = refreshTokenRepository.findByTokenHash(hash);
            if (maybe.isEmpty()) {
                throw new HelloWordException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            } else {
                RefreshToken existing = maybe.get();
                Long memberId = existing.getMemberId();
                try { refreshTokenRepository.revokeAllByMemberId(memberId); } catch (Exception ignore) {}
                try { tokenCacheService.revokeAllAccessTokensForMember(memberId); } catch (Exception ignore) {}
                log.warn("Refresh token reuse detected for memberId={} tokenHash={}", memberId, hash);
                throw new HelloWordException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
            }
        }
    }

    @Transactional
    public void logout(LogoutRequest req, String accessToken) {
        if (req.refreshToken() != null && !req.refreshToken().isBlank()) {
            String hash = TokenHashes.sha256B64(req.refreshToken());
            refreshTokenRepository.findByTokenHash(hash).ifPresent(RefreshToken::revoke);
        }
        if (accessToken != null && !accessToken.isBlank()) {
            long remain = jwtProvider.getAccessTokenRemainingSeconds(accessToken);
            tokenCacheService.blacklistAccessToken(accessToken, remain);
        }
    }

    @Transactional
    public void withdraw(Long memberId) {
        refreshTokenRepository.revokeAllByMemberId(memberId);
        if (!memberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        memberRepository.deleteById(memberId);
    }

    // --- 기존 헬퍼들 ---
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

    private Member createMemberWithRetry(String googleEmail, String googleName) {
        for (int i = 0; i < 5; i++) {
            try {
                Member toSave = Member.builder()
                        .googleEmail(googleEmail)
                        .nickname(generateNickname())
                        .build();
                return memberRepository.save(toSave);
            } catch (DataIntegrityViolationException ex) {
                Member existing = memberRepository.findByGoogleEmail(googleEmail).orElse(null);
                if (existing != null) return existing;
                if (i == 4) throw ex;
            }
        }
        throw new IllegalStateException("회원 생성 재시도 초과");
    }
}
