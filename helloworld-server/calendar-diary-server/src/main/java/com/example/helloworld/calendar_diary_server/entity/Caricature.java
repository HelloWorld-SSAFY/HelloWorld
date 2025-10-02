// entity/Caricature.java
package com.example.helloworld.calendar_diary_server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "caricatures",
        indexes = @Index(name = "idx_caricatures_diaryphoto", columnList = "diary_photo_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Caricature {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diary_photo_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_caricature_diaryphoto"))
    private com.example.helloworld.calendar_diary_server.entity.DiaryPhoto diaryPhoto;

    @Column(name = "image_key", nullable = false, columnDefinition = "TEXT")
    private String imageKey;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;
}
