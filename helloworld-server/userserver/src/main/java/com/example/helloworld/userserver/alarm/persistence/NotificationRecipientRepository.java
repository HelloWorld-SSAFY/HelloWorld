package com.example.helloworld.userserver.alarm.persistence;

import com.example.helloworld.userserver.alarm.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {
    Optional<NotificationRecipient> findByAlarmIdAndRecipientUserId(Long alarmId, Long recipientUserId);

    @Modifying
    @Query(value = """
    INSERT INTO notification_recipients
      (alarm_id, recipient_user_id, status, message_id, fail_reason, created_at, sent_at)
    VALUES
      (:alarmId, :userId, :status, :messageId, :failReason, now(),
       CASE WHEN :status = 'SENT' THEN now() ELSE NULL END)
    ON CONFLICT (alarm_id, recipient_user_id)
    DO UPDATE SET
      status = EXCLUDED.status,
      message_id = EXCLUDED.message_id,
      fail_reason = EXCLUDED.fail_reason,
      sent_at = CASE WHEN EXCLUDED.status = 'SENT' THEN now() ELSE NULL END
    """, nativeQuery = true)
    int upsert(@Param("alarmId") Long alarmId,
               @Param("userId") Long userId,
               @Param("status") String status,
               @Param("messageId") String messageId,
               @Param("failReason") String failReason);

}