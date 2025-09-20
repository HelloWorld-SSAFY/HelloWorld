package com.example.helloworld.userserver.member.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.time.LocalDate;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id_b", nullable = true)
    private Member userB;

    @Column(name = "pregnancy_week")
    private Integer pregnancyWeek;

    @Column(name = "due_date")
    private Timestamp dueDate;

    @Column(name="menstrual_date")
    private LocalDate menstrualDate;

    @Column(name="is_childbirth", nullable=false)
    private boolean isChildbirth;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    public void updateSharing(Integer week, Timestamp due,                          java.time.LocalDate menstrualDate,
                              Boolean isChildbirth) {
        this.pregnancyWeek = week;
        this.dueDate = due;
        this.menstrualDate = menstrualDate;
        this.isChildbirth = Boolean.TRUE.equals(isChildbirth);
    }

    public void setUserB(Member userB) { this.userB = userB; }
}



