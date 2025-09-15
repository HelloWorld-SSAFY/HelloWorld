package com.example.helloworld.userserver.member.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

@Entity
@Table(name = "couples")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class Couple {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "couple_id")
    private Long id;

    // 실무에선 ManyToOne을 권장(매핑 단순, FK 제약은 DB가 보장)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id_a", nullable = false,
            foreignKey = @ForeignKey(name = "fk_couple_user_a"))
    private Member userA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id_b")
    private Member userB;

    @Column(name = "pregnancy_week")
    private Integer pregnancyWeek;

    @Column(name = "due_date")
    private Timestamp dueDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    public void updateSharing(Integer week, Timestamp due) {
        this.pregnancyWeek = week;
        this.dueDate = due;
    }
}



