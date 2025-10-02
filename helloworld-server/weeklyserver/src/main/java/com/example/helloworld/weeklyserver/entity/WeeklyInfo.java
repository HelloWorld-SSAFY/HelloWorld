package com.example.helloworld.weeklyserver.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name="weekly_info")
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyInfo {
    @Id
    @Column(name = "week_no")
    private Integer weekNo;

    @Column(name = "info_text", nullable=false, length=500)
    private String infoText;

    private Integer orderNo;
    @Column(columnDefinition="timestamptz") private Instant updatedAt;
}
