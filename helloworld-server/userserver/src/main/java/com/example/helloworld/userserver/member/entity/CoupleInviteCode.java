package com.example.helloworld.userserver.member.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(name="couple_invite_codes",
        indexes = { @Index(name="idx_invite_expires_at", columnList="expires_at") })
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CoupleInviteCode {

    public enum Status { ISSUED, USED, REVOKED, EXPIRED }

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="couple_id", nullable=false,
            foreignKey = @ForeignKey(name="fk_invite_couple"))
    private Couple couple;

    @Column(name="code", nullable=false, unique=true, length=16)
    private String code;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="issuer_user_id", nullable=false,
            foreignKey = @ForeignKey(name="fk_invite_issuer"))
    private Member issuer;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false, length=16)
    private Status status;

    @Column(name="expires_at", nullable=false)
    private Timestamp expiresAt;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="used_by_user_id",
            foreignKey = @ForeignKey(name="fk_invite_used_by"))
    private Member usedBy;

    @Column(name="used_at")
    private Timestamp usedAt;

    public boolean isUsableNow(Instant now) {
        return status == Status.ISSUED && expiresAt.toInstant().isAfter(now);
    }

    public void markUsed(Member male) {
        this.status = Status.USED;
        this.usedBy = male;
        this.usedAt = Timestamp.from(Instant.now());
    }
}

