package com.example.helloworld.healthserver.alarm.repository;

import com.example.helloworld.healthserver.alarm.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {}
