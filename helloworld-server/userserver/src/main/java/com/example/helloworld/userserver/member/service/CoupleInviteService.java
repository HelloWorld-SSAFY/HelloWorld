package com.example.helloworld.userserver.member.service;

import com.example.helloworld.userserver.member.dto.response.CoupleUnlinkResponse;
import com.example.helloworld.userserver.member.util.RandomCode;
import com.example.helloworld.userserver.member.dto.request.CoupleJoinRequest;
import com.example.helloworld.userserver.member.dto.response.CoupleJoinResponse;
import com.example.helloworld.userserver.member.dto.response.InviteCodeIssueResponse;
import com.example.helloworld.userserver.member.entity.Couple;
import com.example.helloworld.userserver.member.entity.CoupleInviteCode;
import com.example.helloworld.userserver.member.entity.Member;
import com.example.helloworld.userserver.member.persistence.CoupleInviteCodeRepository;
import com.example.helloworld.userserver.member.persistence.CoupleRepository;
import com.example.helloworld.userserver.member.persistence.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CoupleInviteService {
    private final MemberRepository memberRepository;
    private final CoupleRepository coupleRepository;
    private final CoupleInviteCodeRepository inviteRepo;

    @Value("${app.invite-code.ttl-minutes:1440}")
    private int inviteTtlMinutes;

    @Value("${app.invite-code.code-length:8}")
    private int inviteCodeLength;

    @Value("${app.invite-code.revoke-previous:true}")
    private boolean inviteRevokePrevious;

    /** 초대코드 발급: 여성(userA)만, userB 비어있을 때 */
    @Transactional
    public InviteCodeIssueResponse issue(Long issuerId) {
        Member issuer = memberRepository.findById(issuerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (issuer.getGender() != Member.Gender.FEMALE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "여성만 초대코드 발급 가능");
        }

        Couple couple = coupleRepository.findByUserAId(issuerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "커플 없음(여성 등록 먼저)"));

        if (couple.getUserB() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 남편이 연동됨");
        }

        if (inviteRevokePrevious) {
            inviteRepo.revokeAllIssuedByCouple(couple.getId());
        }

        String code = uniqueCode(inviteCodeLength);
        Instant exp = Instant.now().plus(Duration.ofMinutes(inviteTtlMinutes));

        CoupleInviteCode ic = CoupleInviteCode.builder()
                .couple(couple)
                .issuer(issuer)
                .code(code)
                .status(CoupleInviteCode.Status.ISSUED)
                .expiresAt(Timestamp.from(exp))
                .build();

        inviteRepo.save(ic);
        return new InviteCodeIssueResponse(code, exp);
    }

    /** 남편이 코드로 합류 */
    @Transactional
    public CoupleJoinResponse join(Long maleId, CoupleJoinRequest req) {
        if (req == null || req.code() == null || req.code().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code는 필수");
        }

        Member male = memberRepository.findById(maleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));


        // 이미 어떤 커플에 속해 있나?
        boolean alreadyInCouple = coupleRepository.findByUserBId(maleId).isPresent()
                || coupleRepository.findByUserAId(maleId).isPresent();
        if (alreadyInCouple) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 커플에 소속되어 있음");
        }

        // 잠금 걸고 코드 확인 → 동시 사용 방지
        CoupleInviteCode ic = inviteRepo.findWithLockByCode(req.code())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 코드"));
        if (!ic.isUsableNow(Instant.now())) {
            // 만료/무효/사용됨 → 존재 숨김
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "유효하지 않은 코드");
        }

        Couple couple = ic.getCouple();
        if (couple.getUserB() != null) {
            // 경합 시점: 이미 다른 남성이 선점
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 남편이 연동됨");
        }

        // 링크
        couple.setUserB(male);
        ic.markUsed(male);

        return new CoupleJoinResponse(couple.getId());
    }


    @Transactional
    public CoupleUnlinkResponse unlink(Long requesterId) {
        // 1) 요청자 로드(영속)
        Member me = memberRepository.findById(requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        // 2) 내가 속한 커플 찾기 (A 우선, 없으면 B)
        Couple couple = coupleRepository.findByUserAId(me.getId())
                .orElseGet(() -> coupleRepository.findByUserBId(me.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "커플을 찾을 수 없음")));

        boolean isA = couple.getUserA() != null && couple.getUserA().getId().equals(me.getId());
        boolean isB = couple.getUserB() != null && couple.getUserB().getId().equals(me.getId());

        // 3) 권한: 커플 당사자만 가능
        if (!isA && !isB) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "커플 당사자만 해제 가능");
        }

        // 4) 이미 해제되어 있으면 idempotent 처리
        if (couple.getUserB() == null) {
            // 여성(userA) 입장에선 이미 해제된 상태. 남성(userB)이면 여기 못 옴.
            return new CoupleUnlinkResponse(couple.getId(), true);
        }

        // 5) 해제: userB 비우기
        couple.setUserB(null);

        // 6) 안전을 위해 미사용 초대코드 전부 무효화(선택적이지만 권장)
        inviteRepo.revokeAllIssuedByCouple(couple.getId());

        // JPA가 커밋 시점에 flush
        return new CoupleUnlinkResponse(couple.getId(), true);
    }

    private String uniqueCode(int len) {
        for (int i=0;i<10;i++) {
            String c = RandomCode.base32(len);
            if (!inviteRepo.existsByCode(c)) return c;
        }
        String c = RandomCode.base32(len+1);
        if (inviteRepo.existsByCode(c)) throw new IllegalStateException("코드 생성 실패");
        return c;
    }
}

