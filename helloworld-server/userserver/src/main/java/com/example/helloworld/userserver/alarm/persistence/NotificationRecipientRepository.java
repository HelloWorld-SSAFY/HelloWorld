package com.example.helloworld.userserver.alarm.persistence;

import com.example.helloworld.userserver.alarm.dto.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {}
