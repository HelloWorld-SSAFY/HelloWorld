package com.example.helloworld.healthserver.alarm.repository;


import com.example.helloworld.healthserver.alarm.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {}
