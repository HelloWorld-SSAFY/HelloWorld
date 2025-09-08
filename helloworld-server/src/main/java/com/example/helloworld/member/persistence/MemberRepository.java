// MemberRepository.java
package com.example.helloworld.member.persistence;

import com.example.helloworld.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByGoogleEmail(String googleEmail);
    boolean existsByNickname(String nickname);
}
