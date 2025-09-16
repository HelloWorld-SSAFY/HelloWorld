package com.example.helloworld.userserver.member.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.time.LocalDate;
@Entity
@Table(name = "members",
        uniqueConstraints = {
                @UniqueConstraint(name="uk_member_google_email", columnNames="google_email"),
                @UniqueConstraint(name="uk_member_nickname", columnNames="nickname")
        })
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class Member {

    public enum Gender { FEMALE, MALE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="google_email", nullable=false, length=255)
    private String googleEmail;

    @Column(name="nickname", length=255)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name="gender", nullable=true, length=10)
    private Gender gender;

    @Column(name="image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name="age")
    private Integer age;

    @Column(name="menstrual_date")
    private LocalDate menstrualDate;

    @Column(name="is_childbirth", nullable=false)
    private boolean isChildbirth;

    @CreationTimestamp
    @Column(name="created_at", nullable=false, updatable=false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name="updated_at", nullable=false)
    private Timestamp updatedAt;

    public void applyRegistration(String nickname, Gender gender,
                                  LocalDate menstrualDate, Boolean isChildbirth, Integer age) {
        this.nickname = nickname;
        this.gender = gender;
        this.menstrualDate = menstrualDate;
        this.isChildbirth = Boolean.TRUE.equals(isChildbirth);
        this.age = age;
    }

    public void ensureImageUrlNotNull() {
        if (this.imageUrl == null) this.imageUrl = "";
    }

    public void updateImageUrl(String url) {
        this.imageUrl = (url == null || url.isBlank()) ? "" : url.trim();
    }

}

