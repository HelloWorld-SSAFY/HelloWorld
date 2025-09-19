package com.example.helloworld.weeklyserver.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.stereotype.Service;


@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name="diet_plans")
public class DietPlan {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY) private Long dietId;
    @Column(nullable=false) private Integer dayNo;      // 1..280
    @Column(nullable=false) private Integer weekNo;     // generated 칼럼이면 읽기전용 매핑
    @Column(nullable=false) private Integer dayInWeek;  // 1..7
    @Column(nullable=false, length=100) private String food;
    @Column(nullable=false, length=600) private String detail;
    private String imgUrl;
}