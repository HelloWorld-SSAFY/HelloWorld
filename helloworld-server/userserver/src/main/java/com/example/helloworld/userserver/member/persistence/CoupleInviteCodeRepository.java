package com.example.helloworld.userserver.member.persistence;

import com.example.helloworld.userserver.member.entity.CoupleInviteCode;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface CoupleInviteCodeRepository extends JpaRepository<CoupleInviteCode, Long> {

    Optional<CoupleInviteCode> findByCode(String code);

    boolean existsByCode(String code);

    // 동시 사용 방지용 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CoupleInviteCode c where c.code = :code")
    Optional<CoupleInviteCode> findWithLockByCode(@Param("code") String code);

    // 같은 커플에서 아직 미사용(ISSUED) 코드가 있으면 무효화 하고 새로 발급하려면 다음 메서드도 사용 가능 (선택)
    @Modifying
    @Query("update CoupleInviteCode c set c.status = com.example.helloworld.userserver.member.entity.CoupleInviteCode$Status.REVOKED where c.couple.id = :coupleId and c.status = com.example.helloworld.userserver.member.entity.CoupleInviteCode$Status.ISSUED")
    int revokeAllIssuedByCouple(@Param("coupleId") Long coupleId);
}
