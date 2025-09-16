package com.example.helloworld.userserver.alarm.persistence;


import com.example.helloworld.userserver.alarm.dto.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {}
