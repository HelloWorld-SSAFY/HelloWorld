package com.example.helloworld.userserver.alarm.persistence;

import com.example.helloworld.userserver.alarm.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {
    Optional<NotificationRecipient> findByAlarmIdAndRecipientUserId(Long alarmId, Long recipientUserId);
}