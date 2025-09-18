// MemberRepository.java
package com.example.helloworld.userserver.member.persistence;

import com.example.helloworld.userserver.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByGoogleEmail(String googleEmail);
    boolean existsByNickname(String nickname);

    // 닉네임 중복 체크 (자기 자신 제외)
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    // 필요 시 조회용
    Optional<Member> findByNickname(String nickname);

}
