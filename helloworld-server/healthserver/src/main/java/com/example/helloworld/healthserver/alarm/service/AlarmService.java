package com.example.helloworld.healthserver.alarm.service;

import com.example.helloworld.healthserver.alarm.dto.AlarmCreateRequest;
import com.example.helloworld.healthserver.alarm.entity.Notification;
import com.example.helloworld.healthserver.alarm.entity.NotificationRecipient;
import com.example.helloworld.healthserver.alarm.repository.NotificationRecipientRepository;
import com.example.helloworld.healthserver.alarm.repository.NotificationRepository;
import com.example.helloworld.healthserver.member.entity.Couple;
import com.example.helloworld.healthserver.member.persistence.CoupleRepository;
import com.example.helloworld.healthserver.notif.FcmClient;
import com.example.helloworld.healthserver.notif.TokenResolver;
import com.google.firebase.messaging.FirebaseMessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlarmService {

    private final NotificationRepository notificationRepo;
    private final NotificationRecipientRepository recipientRepo;
    private final CoupleRepository coupleRepo;
    private final TokenResolver tokenResolver;
    private final FcmClient fcmClient;

    @Transactional
    public Notification queueAndSendToCouple(Long senderUserId, AlarmCreateRequest req) {
        // 1) sender가 속한 커플 찾기
        Couple couple = coupleRepo.findByUserAId(senderUserId)
                .or(() -> coupleRepo.findByUserBId(senderUserId))
                .orElseThrow(() -> new IllegalStateException("연결된 커플이 없습니다."));

        // 2) 부모 알림 저장
        Notification noti = notificationRepo.save(Notification.builder()
                .alarmType(req.alarm_type())
                .couple(couple)
                .alarmTitle(req.alarm_title())
                .alarmMsg(req.alarm_msg())
                .createdAt(Timestamp.from(req.created_at()))
                .build());

        // 3) 수신 대상(A,B) 수집
        List<Long> recipients = new ArrayList<>();
        recipients.add(couple.getUserA().getId());
        if (couple.getUserB() != null) recipients.add(couple.getUserB().getId());

        // 4) 공통 데이터 페이로드
        Map<String,String> data = new HashMap<>();
        data.put("alarm_type", req.alarm_type().name());

        // 5) 사용자별 발송(여러 기기 토큰이 있을 수 있음) → 결과는 사용자 단위로 집계
        for (Long uid : recipients) {
            List<String> tokens = tokenResolver.resolveActiveTokensForUser(uid);

            // 토큰이 없으면 실패 기록
            if (tokens.isEmpty()) {
                recipientRepo.save(NotificationRecipient.builder()
                        .alarmId(noti.getAlarmId())
                        .recipientUserId(uid)
                        .status("FAILED")
                        .failReason("No active FCM token")
                        .build());
                continue;
            }

            boolean anySuccess = false;
            String anyMsgId = null;
            String lastError = null;

            for (String token : tokens) {
                try {
                    String msgId = fcmClient.sendToToken(token, req.alarm_title(), req.alarm_msg(), data);
                    anySuccess = true;
                    anyMsgId = msgId;
                    // 하나 성공하면 다른 토큰은 생략하려면 break;
                } catch (FirebaseMessagingException e) {
                    lastError = e.getMessage();
                }
            }

            NotificationRecipient rec = NotificationRecipient.builder()
                    .alarmId(noti.getAlarmId())
                    .recipientUserId(uid)
                    .build();

            if (anySuccess) rec.markSent(anyMsgId);
            else           rec.markFailed(lastError != null ? lastError : "Delivery failed");

            recipientRepo.save(rec);
        }

        return noti; // 컨트롤러에서 202 Accepted로 응답
    }
}

