package com.example.helloworld.healthserver.alarm.service;

import com.example.helloworld.healthserver.client.UserServerClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final UserServerClient userServerClient;

    @Async
    public void sendEmergencyNotification(Long currentUserId, Integer heartrate) {
        try {
            // 1. user-server에서 내 커플 상세 정보 조회
            UserServerClient.CoupleDetailResponse coupleDetail = userServerClient.getCoupleDetail();
            if (coupleDetail == null || coupleDetail.couple() == null) {
                log.warn("Could not retrieve couple details for user {}", currentUserId);
                return;
            }

            // 2. 파트너 ID 결정 (가독성을 위한 헬퍼 메소드 사용)
            Long partnerId = findPartnerId(currentUserId, coupleDetail);
            if (partnerId == null) {
                log.warn("Partner not found for user {}. Cannot send notification.", currentUserId);
                return;
            }

            // 3. 파트너의 FCM 토큰 목록 조회
            List<String> tokens = userServerClient.getFcmTokens(partnerId).tokens();
            if (tokens == null || tokens.isEmpty()) {
                log.warn("No active FCM tokens found for partner {}", partnerId);
                return;
            }

            // 4. 메시지 생성 및 전송
            String title = "심박수 이상 감지";
            String body = String.format("현재 심박수가 %dBPM을 초과했습니다. 상태를 확인해주세요.", heartrate);
            Map<String, String> dataPayload = Map.of("type", "EMERGENCY", "title", title, "body", body);

            for (String token : tokens) {
                Message message = Message.builder().putAllData(dataPayload).setToken(token).build();
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent FCM message to partner {}: {}", partnerId, response);
            }

        } catch (Exception e) {
            log.error("Failed to send FCM emergency notification for user {}", currentUserId, e);
        }
    }
    /**
     * 커플 상세 정보에서 파트너의 ID를 추출합니다.
     * @return 파트너의 사용자 ID, 찾지 못하면 null
     */
    private Long findPartnerId(Long currentUserId, UserServerClient.CoupleDetailResponse coupleDetail) {
        if (coupleDetail == null || coupleDetail.couple() == null) {
            return null;
        }
        Long userAId = coupleDetail.couple().user_a_id();
        Long userBId = coupleDetail.couple().user_b_id();

        if (Objects.equals(currentUserId, userAId)) {
            return userBId;
        }
        if (Objects.equals(currentUserId, userBId)) {
            return userAId;
        }
        return null;
    }

}
