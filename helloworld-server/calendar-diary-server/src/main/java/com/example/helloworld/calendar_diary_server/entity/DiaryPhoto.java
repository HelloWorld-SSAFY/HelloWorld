package com.example.helloworld.calendar_diary_server.entity;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "diaryphoto",
        indexes = {
                @Index(name = "idx_diaryphoto_diary", columnList = "diary_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaryPhoto {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diary_photo_id")
    private Long diaryPhotoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diary_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_diaryphoto_diary"))
    private Diary diary;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "is_ultrasound", nullable = false)
    private boolean isUltrasound = false;
}
