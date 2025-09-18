package com.example.helloworld.healthserver.persistence;

import com.example.helloworld.healthserver.entity.MaternalHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

public interface MaternalHealthRepository extends JpaRepository<MaternalHealth, Long> {

    List<MaternalHealth> findByCoupleIdOrderByRecordDateDescCreatedAtDesc(Long coupleId);

    List<MaternalHealth> findByCoupleIdAndRecordDateBetweenOrderByRecordDateDescCreatedAtDesc(
            Long coupleId, LocalDate from, LocalDate to);

    Optional<MaternalHealth> findTopByCoupleIdOrderByRecordDateDescCreatedAtDesc(Long coupleId);

    Optional<MaternalHealth> findByIdAndCoupleId(Long id, Long coupleId);
}
