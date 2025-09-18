package com.example.helloworld.userserver.member.persistence;


import com.example.helloworld.userserver.member.entity.Couple;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoupleRepository extends JpaRepository<Couple, Long> {

    // 여성 등록 시 커플 자동생성/재사용을 위해 A 슬롯으로 검색
    Optional<Couple> findByUserAId(Long userId);

    // (참고) 남성 합류/상태 조회용으로 B 슬롯 검색이 필요하면 추가
    Optional<Couple> findByUserBId(Long userId);
}


