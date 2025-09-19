package com.example.helloworld.calendar_diary_server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;





@Entity
@Table(name = "diary",
        indexes = {
                @Index(name = "idx_diary_author_created", columnList = "author_id, created_at"),
                @Index(name = "idx_diary_couple_created", columnList = "couple_id, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Diary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diary_id")
    private Long diaryId;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 10)
    private AuthorRole authorRole;

    @Column(name = "diary_title", length = 255)
    private String diaryTitle;

    @Column(name = "diary_content", columnDefinition = "TEXT")
    private String diaryContent;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate; // 주차/캘린더 조회의 기준


    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    /**
     * ENUM: AuthorRole
     * FATHER, MOTHER
     */
    public enum AuthorRole {
        FATHER, MOTHER
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

}
