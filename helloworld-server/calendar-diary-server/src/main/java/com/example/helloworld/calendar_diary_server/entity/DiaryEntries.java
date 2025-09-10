package com.example.helloworld.calendar_diary_server.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name="diary_entries")
public class DiaryEntries {



    @Id
    @GeneratedValue(generator ="uuid" )
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;   // 외부 서비스 참조

    private UUID coupleId; // 외부 서비스 참조

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false, length = 10)
    private String visibility = "PRIVATE";

    @Column(nullable = false)
    private boolean isDeleted = false;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

}
