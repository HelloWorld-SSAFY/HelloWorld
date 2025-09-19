package com.example.helloworld.weeklyserver.entity;

import com.example.helloworld.weeklyserver.dto.WorkoutType;
import jakarta.persistence.*;
import lombok.*;


@Getter
@Setter
@Builder
@Entity
@Table(name="weekly_workouts")
@AllArgsConstructor
@NoArgsConstructor
public class WeeklyWorkout {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY) private Long workoutId;
    @Column(nullable=false) private Integer weekNo;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private WorkoutType type;
    @Column(length=500) private String textBody;
    @Column(length=200) private String videoTitle;
    @Column(length=500) private String videoUrl;
    @Column(length=500) private String thumbnailUrl;
    private Integer orderNo;

    public String getInfoText() {
        return null;
    }
}
