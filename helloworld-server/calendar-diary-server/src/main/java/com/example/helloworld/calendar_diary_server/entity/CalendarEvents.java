package com.example.helloworld.calendar_diary_server.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter@Setter
@NoArgsConstructor@AllArgsConstructor@Builder
@Table(name="calendar_events")
public class CalendarEvents {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private UUID coupleId;

    @Column(nullable = false, length = 200)
    private String title;

    private String notes;
    private String location;
    private String category = "CUSTOM";
    private boolean allDay = false;

    @Column(nullable = false)
    private Instant startAt;

    private Instant endAt;

    private boolean isDeleted = false;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

}
