package com.example.helloworld.healthserver.alarm.service;

import com.example.helloworld.healthserver.client.UserServerClient;
import com.example.helloworld.healthserver.alarm.dto.AiResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final UserServerClient userClient;
    private final AiClient aiClient;

    // === 기존 호환성 유지 ===
    @Async
    public void sendEmergencyTripleAndRecord(Long alarmId, Long measuredUserId, int hr, String title, String body) {
        sendEmergencyTripleAndRecord(alarmId, measuredUserId, hr, null, title, body);
    }

    @Async
    public void sendEmergencyTripleAndRecord(Long alarmId, Long measuredUserId, int hr, Integer stress, String title, String body) {
        try {
            // 토큰 수집
            TokenInfo tokenInfo = collectTokens(measuredUserId);

            // 메시지 데이터 생성
            Map<String,String> data = createMessageData("EMERGENCY", title, body, hr, stress);

            // FCM 발송 및 결과 수집
            var results = sendToAllDevices(tokenInfo, data, measuredUserId);

            // 결과를 유저서버에 기록
            recordResults(alarmId, measuredUserId, tokenInfo.partnerId, results);

        } catch (Exception e) {
            log.error("[FCM] sendEmergencyTripleAndRecord error alarmId={} user={}", alarmId, measuredUserId, e);
        }
    }

    @Async
    public void sendReminderNotification(Long userId, String title, String body) {
        try {
            TokenInfo tokenInfo = collectTokens(userId);
            Map<String,String> data = Map.of("type", "REMINDER", "title", title, "body", body);

            sendIfPresent(tokenInfo.androidToken, data, userId, "ANDROID_REMINDER");
            sendIfPresent(tokenInfo.watchToken, data, userId, "WATCH_REMINDER");

        } catch (Exception e) {
            log.error("[FCM-REMINDER] send failed user={}", userId, e);
        }
    }

    @Async
    public void sendEmergencyNotification(Long userId, Integer hr) {
        sendEmergencyTriple(userId, hr != null ? hr : 0);
    }

    @Async
    public void sendEmergencyTriple(Long measuredUserId, int hr) {
        sendEmergencyTriple(measuredUserId, hr, null);
    }

    @Async
    public void sendEmergencyTriple(Long measuredUserId, int hr, Integer stress) {
        try {
            // AI 서버 호출
            AiResponse aiResponse = callAiServer(measuredUserId, hr, stress);

            if (aiResponse == null || !aiResponse.isOk()) {
                log.warn("[AI] AI server response failed for user={}", measuredUserId);
                return;
            }

            // 이상 감지되지 않으면 알림 발송 안함
            if (aiResponse.getAnomaly() == null || !aiResponse.getAnomaly()) {
                log.info("[AI] Normal state for user={}, no notification needed", measuredUserId);
                return;
            }

            // 토큰 수집
            TokenInfo tokenInfo = collectTokens(measuredUserId);

            // AI 응답 기반 메시지 생성
            String title = createTitle(aiResponse.getMode());
            String body = createBody(aiResponse.getMode(), aiResponse.getReasons(), hr, stress);
            Map<String,String> data = createMessageData("EMERGENCY", title, body, hr, stress);

            // FCM 발송
            sendToAllDevices(tokenInfo, data, measuredUserId);

        } catch (Exception e) {
            log.error("[FCM] sendEmergencyTriple error user={}", measuredUserId, e);
        }
    }

    // === AI 서버 호출 ===
    private AiResponse callAiServer(Long userId, int hr, Integer stress) {
        try {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("hr", hr);
            if (stress != null) {
                metrics.put("stress", stress);
            }

            Map<String, Object> request = Map.of(
                    "user_ref", "u" + userId,
                    "ts", Instant.now().atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    "metrics", metrics
            );

            var response = aiClient.sendTelemetry(request);
            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            log.error("[AI] Failed to call AI server for user={}", userId, e);
            return null;
        }
    }

    // === 메시지 생성 ===
    private String createTitle(String mode) {
        if (mode == null) return "건강 알림";

        switch (mode.toLowerCase()) {
            case "emergency": return "응급 상황 감지";
            case "restrict": return "건강 이상 감지";
            case "cooldown": return "건강 주의";
            default: return "건강 알림";
        }
    }

    private String createBody(String mode, List<String> reasons, int hr, Integer stress) {
        boolean hasHrIssue = reasons != null && reasons.stream().anyMatch(r -> r.toUpperCase().contains("HR"));
        boolean hasStressIssue = reasons != null && reasons.stream().anyMatch(r -> r.toUpperCase().contains("STRESS"));

        String stressText = "";
        if (hasStressIssue) {
            stressText = stress != null ? String.format(", 스트레스 지수: %d", stress) : ", 스트레스 수치 높음";
        }

        if (mode == null) {
            return String.format("현재 심박수: %dBPM%s. 상태를 확인해주세요.", hr, stressText);
        }

        switch (mode.toLowerCase()) {
            case "emergency":
                return String.format("응급 상황이 감지되었습니다. 현재 심박수: %dBPM%s. 즉시 상태를 확인해주세요.", hr, stressText);
            case "restrict":
                return String.format("건강 상태에 이상이 감지되었습니다. 현재 심박수: %dBPM%s. 상태를 확인해주세요.", hr, stressText);
            case "cooldown":
                return String.format("안정이 필요합니다! 현재 심박수: %dBPM%s. 휴식을 취해주세요.", hr, stressText);
            default:
                return String.format("현재 심박수: %dBPM%s. 상태를 확인해주세요.", hr, stressText);
        }
    }

    // === 토큰 수집 ===
    private TokenInfo collectTokens(Long measuredUserId) {
        // 본인 토큰
        String androidToken = null, watchToken = null;
        var twoResp = userClient.latestTwo(measuredUserId);
        if (twoResp != null && twoResp.getStatusCode().is2xxSuccessful() && twoResp.getBody() != null) {
            androidToken = twoResp.getBody().androidToken();
            watchToken = twoResp.getBody().watchToken();
        } else {
            log.warn("[FCM] latestTwo empty user={}", measuredUserId);
        }

        // 파트너 토큰
        Long partnerId = null;
        String partnerAndroidToken = null;
        var pidResp = userClient.partnerId(measuredUserId);
        if (pidResp != null && pidResp.getStatusCode().is2xxSuccessful() && pidResp.getBody() != null) {
            partnerId = pidResp.getBody().partnerId();
            var pResp = userClient.latestByPlatform(partnerId, "ANDROID");
            if (pResp != null && pResp.getStatusCode().is2xxSuccessful() && pResp.getBody() != null) {
                partnerAndroidToken = pResp.getBody().token();
            } else {
                log.warn("[FCM] partner ANDROID token empty partnerId={}", partnerId);
            }
        } else {
            log.warn("[FCM] partnerId not found for user={}", measuredUserId);
        }

        return new TokenInfo(androidToken, watchToken, partnerAndroidToken, partnerId);
    }

    // === 메시지 데이터 생성 ===
    private Map<String,String> createMessageData(String type, String title, String body, int hr, Integer stress) {
        Map<String,String> data = new HashMap<>();
        data.put("type", type);
        data.put("title", title != null ? title : "심박수 이상 감지");
        data.put("body", body != null ? body : String.format("현재 심박수가 %dBPM을 초과했습니다. 상태를 확인해주세요.", hr));
        data.put("hr", Integer.toString(hr));
        if (stress != null) {
            data.put("stress", Integer.toString(stress));
        }
        return data;
    }

    // === FCM 발송 ===
    private SendResults sendToAllDevices(TokenInfo tokenInfo, Map<String,String> data, Long measuredUserId) {
        var androidResult = sendOne(tokenInfo.androidToken, data, measuredUserId, "ANDROID");
        var watchResult = sendOne(tokenInfo.watchToken, data, measuredUserId, "WATCH");
        var partnerResult = sendOne(tokenInfo.partnerAndroidToken, data, tokenInfo.partnerId, "PARTNER_ANDROID");

        return new SendResults(androidResult, watchResult, partnerResult);
    }

    private void sendIfPresent(String token, Map<String,String> data, Long ownerUserId, String label) {
        if (token == null || token.isBlank()) {
            log.debug("[FCM] skip empty token label={} user={}", label, ownerUserId);
            return;
        }
        try {
            Message msg = Message.builder().putAllData(data).setToken(token).build();
            String res = FirebaseMessaging.getInstance().send(msg);
            log.info("[FCM] ok label={} user={} res={}", label, ownerUserId, res);
        } catch (com.google.firebase.messaging.FirebaseMessagingException e) {
            log.warn("[FCM] fail label={} user={} code={}", label, ownerUserId, e.getMessagingErrorCode(), e);
        } catch (Exception e) {
            log.error("[FCM] fail label={} user={}", label, ownerUserId, e);
        }
    }

    private SendResult sendOne(String token, Map<String,String> data, Long ownerUserId, String label) {
        if (token == null || token.isBlank()) {
            log.debug("[FCM] skip empty token label={} user={}", label, ownerUserId);
            return new SendResult(false, null, "NO_TOKEN");
        }
        try {
            var msg = Message.builder().putAllData(data).setToken(token).build();
            String res = FirebaseMessaging.getInstance().send(msg);
            log.info("[FCM] ok label={} user={} msgId={}", label, ownerUserId, res);
            return new SendResult(true, res, null);
        } catch (com.google.firebase.messaging.FirebaseMessagingException e) {
            var code = e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : "UNKNOWN";
            log.warn("[FCM] fail label={} user={} code={}", label, ownerUserId, code, e);
            return new SendResult(false, null, code);
        } catch (Exception e) {
            log.error("[FCM] error label={} user={}", label, ownerUserId, e);
            return new SendResult(false, null, "EXCEPTION");
        }
    }

    // === 결과 기록 ===
    private void recordResults(Long alarmId, Long measuredUserId, Long partnerId, SendResults results) {
        // 본인 결과 기록
        boolean meSent = results.androidResult.success || results.watchResult.success;
        String meMsg = firstNonNull(results.androidResult.messageId, results.watchResult.messageId);
        String meErr = meSent ? null : firstNonNull(results.androidResult.errorCode, results.watchResult.errorCode, "NO_TOKEN");

        userClient.upsertRecipient(new UserServerClient.UpsertReq(
                alarmId, measuredUserId, meSent ? "SENT" : "FAILED", meMsg, meErr));

        // 파트너 결과 기록
        if (partnerId != null) {
            boolean pSent = results.partnerResult.success;
            String pMsg = results.partnerResult.messageId;
            String pErr = pSent ? null : firstNonNull(results.partnerResult.errorCode, "NO_TOKEN");

            userClient.upsertRecipient(new UserServerClient.UpsertReq(
                    alarmId, partnerId, pSent ? "SENT" : "FAILED", pMsg, pErr));
        }
    }

    // === 헬퍼 메서드 ===
    private static String firstNonNull(String... s) {
        for (var x : s) {
            if (x != null && !x.isBlank()) return x;
        }
        return null;
    }

    // === 내부 클래스들 ===
    private static class TokenInfo {
        final String androidToken;
        final String watchToken;
        final String partnerAndroidToken;
        final Long partnerId;

        TokenInfo(String androidToken, String watchToken, String partnerAndroidToken, Long partnerId) {
            this.androidToken = androidToken;
            this.watchToken = watchToken;
            this.partnerAndroidToken = partnerAndroidToken;
            this.partnerId = partnerId;
        }
    }

    private static class SendResult {
        final boolean success;
        final String messageId;
        final String errorCode;

        SendResult(boolean success, String messageId, String errorCode) {
            this.success = success;
            this.messageId = messageId;
            this.errorCode = errorCode;
        }
    }

    private static class SendResults {
        final SendResult androidResult;
        final SendResult watchResult;
        final SendResult partnerResult;

        SendResults(SendResult androidResult, SendResult watchResult, SendResult partnerResult) {
            this.androidResult = androidResult;
            this.watchResult = watchResult;
            this.partnerResult = partnerResult;
        }
    }

    // === AI 클라이언트 ===
    @FeignClient(name = "aiClient", url = "${ai.server.base-url}")
    public interface AiClient {
        @PostMapping("/v1/telemetry")
        ResponseEntity<AiResponse> sendTelemetry(@RequestBody Map<String, Object> request);
    }
}