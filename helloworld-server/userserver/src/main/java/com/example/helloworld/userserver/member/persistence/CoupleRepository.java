package com.example.helloworld.userserver.member.persistence;


import com.example.helloworld.userserver.member.entity.Couple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CoupleRepository extends JpaRepository<Couple, Long> {

    Optional<Couple> findByUserA_IdOrUserB_Id(Long userIdA, Long userIdB);
    // 여성 등록 시 커플 자동생성/재사용을 위해 A 슬롯으로 검색
    Optional<Couple> findByUserAId(Long userId);

    // (참고) 남성 합류/상태 조회용으로 B 슬롯 검색이 필요하면 추가
    Optional<Couple> findByUserBId(Long userId);

    // userId로 커플 조회 (서비스 호환용)
    @Query("select c from Couple c where c.userA.id = :uid or c.userB.id = :uid")
    Optional<Couple> findByUserId(@Param("uid") Long userId);

    // 파트너 ID 바로 얻기 (프로젝션)
    @Query("""
           select case 
                    when c.userA.id = :uid then c.userB.id 
                    when c.userB.id = :uid then c.userA.id 
                  end
           from Couple c 
           where c.userA.id = :uid or c.userB.id = :uid
           """)
    Optional<Long> findPartnerIdByUserId(@Param("uid") Long userId);

}


