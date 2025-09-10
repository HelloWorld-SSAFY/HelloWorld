package com.example.helloworld.calendar_diary_server.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_diary_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(EventDiaryLinks.PK.class)
public class EventDiaryLinks {
    @Id
    private UUID eventId;

    @Id
    private UUID diaryId;

    private Instant linkedAt = Instant.now();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private UUID eventId;
        private UUID diaryId;
    }
}
