// Member.java
package com.example.helloworld.member.domain;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Time;
import java.sql.Timestamp;

@Entity
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(name="uk_member_google_email", columnNames="google_email"),
                @UniqueConstraint(name="uk_member_nickname", columnNames="nickname")
        }
)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="google_email", nullable=false)
    private String googleEmail;

    @Column(name="name", nullable=false)
    private String name;

    @Column(name="nickname", nullable=false)
    private String nickname;

    @Column(name="gender", nullable = false)
    private String gender;

    @Column(name="image_url", nullable = false)
    private String imageUrl;

    @Column(name="created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name="updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name="menstrual_date", nullable = false)
    private Timestamp menstrualDate;

    @Column(name="is_childbirth", nullable = false)
    private boolean isChildbirth;

}
