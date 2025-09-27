package com.example.helloworld.healthserver.alarm.service;

import com.example.helloworld.healthserver.client.UserServerClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final UserServerClient userClient;

    @Async
    public void sendEmergencyTripleAndRecord(Long alarmId, Long measuredUserId, int hr, String title, String body) {
        try {
            // 1) 본인 ANDROID/WATCH 토큰
            String androidToken = null, watchToken = null;
            var two = userClient.latestTwo(measuredUserId);
            if (two != null && two.getStatusCode().is2xxSuccessful() && two.getBody() != null) {
                androidToken = two.getBody().androidToken();
                watchToken   = two.getBody().watchToken();
            }

            // 2) 파트너 ANDROID 토큰
            Long partnerId = null;
            String partnerAndroidToken = null;
            var pid = userClient.partnerId(measuredUserId);
            if (pid != null && pid.getStatusCode().is2xxSuccessful() && pid.getBody() != null) {
                partnerId = pid.getBody().partnerId();
                var p = userClient.latestByPlatform(partnerId, "ANDROID");
                if (p != null && p.getStatusCode().is2xxSuccessful() && p.getBody() != null) {
                    partnerAndroidToken = p.getBody().token();
                }
            }

            // 3) 공통 데이터
            Map<String,String> data = Map.of(
                    "type","EMERGENCY",
                    "title", title != null ? title : "심박수 이상 감지",
                    "body",  body  != null ? body  : String.format("현재 심박수가 %dBPM을 초과했습니다. 상태를 확인해주세요.", hr)
            );

            // 4) 3건 전송 + 결과 수집
            var rMeA     = sendOne(androidToken,        data, measuredUserId, "ANDROID");
            var rMeW     = sendOne(watchToken,          data, measuredUserId, "WATCH");
            var rPartner = sendOne(partnerAndroidToken, data, partnerId,      "PARTNER_ANDROID");

            // 5) 집계 후 유저서버에 업서트
            boolean meSent = rMeA.success || rMeW.success;
            String  meMsg  = firstNonNull(rMeA.messageId, rMeW.messageId);
            String  meErr  = meSent ? null : firstNonNull(rMeA.errorCode, rMeW.errorCode, reasonIfEmpty(androidToken, watchToken));

            userClient.upsertRecipient(new UserServerClient.UpsertReq(
                    alarmId, measuredUserId, meSent ? "SENT" : "FAILED", meMsg, meErr));

            if (partnerId != null) {
                boolean pSent = rPartner.success;
                String  pMsg  = rPartner.messageId;
                String  pErr  = pSent ? null : firstNonNull(rPartner.errorCode, reasonIfEmpty(partnerAndroidToken));
                userClient.upsertRecipient(new UserServerClient.UpsertReq(
                        alarmId, partnerId, pSent ? "SENT" : "FAILED", pMsg, pErr));
            } else {
                log.warn("[FCM] partnerId not found for user={}", measuredUserId);
            }

        } catch (Exception e) {
            log.error("[FCM] sendEmergencyTripleAndRecord error alarmId={} user={}", alarmId, measuredUserId, e);
        }
    }

    // === 헬퍼: 토큰 존재 시만 전송 ===
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
            // 필요 시: SENDER_ID_MISMATCH 등 코드별 처리 추가 가능
            log.warn("[FCM] fail label={} user={} code={}", label, ownerUserId, e.getMessagingErrorCode(), e);
        } catch (Exception e) {
            log.error("[FCM] fail label={} user={}", label, ownerUserId, e);
        }
    }

    @Async
    public void sendReminderNotification(Long userId, String title, String body) {
        try {
            // 1) 유저의 ANDROID / WATCH 최신 1개씩 조회
            String androidToken = null, watchToken = null;
            var resp = userClient.latestTwo(userId);
            if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                androidToken = resp.getBody().androidToken();
                watchToken   = resp.getBody().watchToken();
            } else {
                log.warn("[FCM-REMINDER] latestTwo empty user={}", userId);
            }

            // 2) 공통 데이터 페이로드
            Map<String,String> data = Map.of(
                    "type", "REMINDER",
                    "title", title,
                    "body",  body
            );

            // 3) 두 군데 발송 (모바일 1, 워치 1)
            sendIfPresent(androidToken, data, userId, "ANDROID_REMINDER");
            sendIfPresent(watchToken,   data, userId, "WATCH_REMINDER");

        } catch (Exception e) {
            log.error("[FCM-REMINDER] send failed user={}", userId, e);
        }
    }

    private static class SendResult {
        final boolean success; final String messageId; final String errorCode;
        SendResult(boolean s, String id, String err){ this.success=s; this.messageId=id; this.errorCode=err; }
    }

    private SendResult sendOne(String token, Map<String,String> data, Long ownerUserId, String label) {
        if (token == null || token.isBlank()) {
            log.debug("[FCM] skip empty token label={} user={}", label, ownerUserId);
            return new SendResult(false, null, "NO_TOKEN");
        }
        try {
            var msg = Message.builder().putAllData(data).setToken(token).build();
            String res = FirebaseMessaging.getInstance().send(msg); // messageId
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

    /**
     * AI 서버 응답을 받아 위급 상황별 알림 전송 (확장된 버전)
     */
    @Async
    public void sendEmergencyTripleWithAiResponse(Long measuredUserId, int hr, String mode, String riskLevel, java.util.List<String> reasons) {
        try {
            // 1) 본인 ANDROID / WATCH 최신 토큰
            String androidToken = null, watchToken = null;
            var twoResp = userClient.latestTwo(measuredUserId);
            if (twoResp != null && twoResp.getStatusCode().is2xxSuccessful() && twoResp.getBody() != null) {
                androidToken = twoResp.getBody().androidToken();
                watchToken   = twoResp.getBody().watchToken();
            } else {
                log.warn("[FCM] latestTwo empty user={}", measuredUserId);
            }

            // 2) 파트너 ANDROID 최신 토큰
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

            // 3) AI 서버 응답 기반 위급 메시지 생성
            EmergencyMessage emergencyMsg = generateEmergencyMessage(hr, mode, riskLevel, reasons);

            // 4) 본인용 데이터 (자세한 정보 포함)
            Map<String,String> selfData = Map.of(
                    "type", "EMERGENCY",
                    "mode", mode != null ? mode : "normal",
                    "risk_level", riskLevel != null ? riskLevel : "low",
                    "title", emergencyMsg.title,
                    "body", emergencyMsg.selfBody
            );

            // 5) 파트너용 데이터 (걱정과 행동 유도 메시지)
            Map<String,String> partnerData = Map.of(
                    "type", "EMERGENCY",
                    "mode", mode != null ? mode : "normal",
                    "risk_level", riskLevel != null ? riskLevel : "low",
                    "title", emergencyMsg.title,
                    "body", emergencyMsg.partnerBody
            );

            // 6) 본인에게 발송 (ANDROID, WATCH)
            sendIfPresent(androidToken, selfData, measuredUserId, "ANDROID");
            sendIfPresent(watchToken,   selfData, measuredUserId, "WATCH");

            // 7) 파트너에게 발송 (ANDROID) - emergency/restrict 모드일 때만
            if ("emergency".equals(mode) || "restrict".equals(mode)) {
                sendIfPresent(partnerAndroidToken, partnerData, partnerId, "PARTNER_ANDROID");
                log.info("[FCM] Emergency/Restrict mode - notified partner for user={}", measuredUserId);
            } else {
                log.debug("[FCM] Normal mode - skipping partner notification for user={}", measuredUserId);
            }

        } catch (Exception e) {
            log.error("[FCM] sendEmergencyTripleWithAiResponse error user={}", measuredUserId, e);
        }
    }

    @Async
    public void sendEmergencyTriple(Long measuredUserId, int hr) {
        try {
            // 1) 본인 ANDROID / WATCH 최신 토큰
            String androidToken = null, watchToken = null;
            var twoResp = userClient.latestTwo(measuredUserId);
            if (twoResp != null && twoResp.getStatusCode().is2xxSuccessful() && twoResp.getBody() != null) {
                androidToken = twoResp.getBody().androidToken();
                watchToken   = twoResp.getBody().watchToken();
            } else {
                log.warn("[FCM] latestTwo empty user={}", measuredUserId);
            }

            // 2) 파트너 ANDROID 최신 토큰
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

            // 3) AI 서버 응답 기반 위급 메시지 생성 (기본값으로 처리)
            EmergencyMessage emergencyMsg = generateEmergencyMessage(hr, "normal", "low", null);

            // 4) 본인용 데이터 (자세한 정보 포함)
            Map<String,String> selfData = Map.of(
                    "type", "EMERGENCY",
                    "title", emergencyMsg.title,
                    "body", emergencyMsg.selfBody
            );

            // 5) 파트너용 데이터 (걱정과 행동 유도 메시지)
            Map<String,String> partnerData = Map.of(
                    "type", "EMERGENCY",
                    "title", emergencyMsg.title,
                    "body", emergencyMsg.partnerBody
            );

            // 6) 본인에게 발송 (ANDROID, WATCH)
            sendIfPresent(androidToken, selfData, measuredUserId, "ANDROID");
            sendIfPresent(watchToken,   selfData, measuredUserId, "WATCH");

            // 7) 파트너에게 발송 (ANDROID)
            sendIfPresent(partnerAndroidToken, partnerData, partnerId, "PARTNER_ANDROID");

        } catch (Exception e) {
            log.error("[FCM] sendEmergencyTriple error user={}", measuredUserId, e);
        }
    }

    /**
     * AI 서버 응답에 따른 위급 상황별 메시지 생성
     */
    private EmergencyMessage generateEmergencyMessage(int hr, String mode, String riskLevel, java.util.List<String> reasons) {
        String title;
        String selfBody;
        String partnerBody;

        // AI 서버 응답의 mode에 따른 분기 처리
        switch (mode != null ? mode.toLowerCase() : "normal") {
            case "emergency":
                return generateEmergencyModeMessage(hr, reasons);
            case "restrict":
                return generateRestrictModeMessage(hr, reasons);
            case "normal":
            default:
                return generateNormalModeMessage(hr, riskLevel);
        }
    }

    /**
     * Emergency 모드 메시지 생성 (critical 상황)
     */
    private EmergencyMessage generateEmergencyModeMessage(int hr, java.util.List<String> reasons) {
        String reasonText = reasons != null && !reasons.isEmpty()
                ? String.join(", ", reasons)
                : "지속적인 이상 수치";

        String title = "🚨 응급 상황 감지";
        String selfBody = String.format(
                "현재 심박수 %dBPM - 응급 상황이 감지되었습니다.\n" +
                        "감지 사유: %s\n" +
                        "즉시 안전한 곳으로 이동하여 휴식을 취하고, 필요시 응급실에 연락하세요.",
                hr, reasonText
        );
        String partnerBody = String.format(
                "🚨 파트너에게 응급 상황이 감지되었습니다!\n" +
                        "심박수: %dBPM\n" +
                        "감지 사유: %s\n" +
                        "즉시 연락하여 안전 상태를 확인해주세요.",
                hr, reasonText
        );

        return new EmergencyMessage(title, selfBody, partnerBody);
    }

    /**
     * Restrict 모드 메시지 생성 (이상 감지, 3회 연속)
     */
    private EmergencyMessage generateRestrictModeMessage(int hr, java.util.List<String> reasons) {
        String reasonText = reasons != null && !reasons.isEmpty()
                ? String.join(", ", reasons)
                : "연속 이상 수치";

        String title = "⚠️ 건강 이상 감지";
        String selfBody = String.format(
                "현재 심박수 %dBPM - 건강 이상이 감지되었습니다.\n" +
                        "감지 사유: %s\n" +
                        "즉시 활동을 중단하고 호흡을 정리하며 충분한 휴식을 취해주세요.",
                hr, reasonText
        );
        String partnerBody = String.format(
                "⚠️ 파트너의 건강 이상이 감지되었습니다.\n" +
                        "심박수: %dBPM\n" +
                        "감지 사유: %s\n" +
                        "상태를 확인하고 도움이 필요한지 연락해보세요.",
                hr, reasonText
        );

        return new EmergencyMessage(title, selfBody, partnerBody);
    }

    /**
     * Normal 모드 메시지 생성 (기존 심박수 범위별 처리)
     */
    private EmergencyMessage generateNormalModeMessage(int hr, String riskLevel) {
        String title;
        String selfBody;
        String partnerBody;

        // risk_level 고려한 추가 분기
        if ("high".equals(riskLevel)) {
            title = "⚠️ 심박수 주의";
            selfBody = String.format("현재 심박수가 %dBPM으로 주의가 필요합니다. 천천히 호흡하며 휴식을 취해주세요.", hr);
            partnerBody = String.format("파트너의 심박수가 %dBPM으로 평소보다 높습니다. 상태를 확인해보세요.", hr);
        } else if (hr >= 180) {
            // 극도로 높은 심박수 (180 이상)
            title = "🚨 심각한 심박수 이상";
            selfBody = String.format("현재 심박수가 %dBPM으로 매우 위험한 수준입니다. 즉시 휴식을 취하고 필요시 응급실에 연락하세요.", hr);
            partnerBody = String.format("파트너의 심박수가 %dBPM으로 위험한 상태입니다. 즉시 연락하여 상태를 확인해주세요.", hr);
        } else if (hr >= 160) {
            // 매우 높은 심박수 (160-179)
            title = "⚠️ 심박수 위험 경고";
            selfBody = String.format("현재 심박수가 %dBPM으로 높습니다. 즉시 활동을 중단하고 안전한 곳에서 휴식을 취하세요.", hr);
            partnerBody = String.format("파트너의 심박수가 %dBPM으로 높은 상태입니다. 안전 상태를 확인해주세요.", hr);
        } else if (hr >= 140) {
            // 높은 심박수 (140-159)
            title = "⚠️ 심박수 주의";
            selfBody = String.format("현재 심박수가 %dBPM입니다. 천천히 호흡하며 휴식을 취해주세요.", hr);
            partnerBody = String.format("파트너의 심박수가 %dBPM으로 평소보다 높습니다. 상태를 확인해보세요.", hr);
        } else if (hr >= 120) {
            // 중간 수준 높은 심박수 (120-139)
            title = "💗 심박수 알림";
            selfBody = String.format("현재 심박수가 %dBPM입니다. 잠시 휴식을 취하시는 것을 권장합니다.", hr);
            partnerBody = String.format("파트너의 심박수가 %dBPM으로 조금 높습니다.", hr);
        } else if (hr <= 40) {
            // 매우 낮은 심박수 (40 이하)
            title = "⚠️ 심박수 저하 경고";
            selfBody = String.format("현재 심박수가 %dBPM으로 매우 낮습니다. 몸에 이상이 없는지 확인하고 필요시 의료진에게 연락하세요.", hr);
            partnerBody = String.format("파트너의 심박수가 %dBPM으로 매우 낮은 상태입니다. 상태를 확인해주세요.", hr);
        } else if (hr <= 50) {
            // 낮은 심박수 (41-50)
            title = "💙 심박수 저하 알림";
            selfBody = String.format("현재 심박수가 %dBPM으로 낮습니다. 몸 상태를 확인해보세요.", hr);
            partnerBody = String.format("파트너의 심박수가 %dBPM으로 평소보다 낮습니다.", hr);
        } else {
            // 기본 메시지 (51-119)
            title = "💗 심박수 알림";
            selfBody = String.format("현재 심박수가 %dBPM입니다. 상태를 확인해주세요.", hr);
            partnerBody = String.format("파트너의 심박수가 %dBPM입니다.", hr);
        }

        return new EmergencyMessage(title, selfBody, partnerBody);
    }

    /**
     * 위급 상황 메시지 정보를 담는 내부 클래스
     */
    private static class EmergencyMessage {
        final String title;
        final String selfBody;      // 본인에게 보낼 메시지
        final String partnerBody;   // 파트너에게 보낼 메시지

        EmergencyMessage(String title, String selfBody, String partnerBody) {
            this.title = title;
            this.selfBody = selfBody;
            this.partnerBody = partnerBody;
        }
    }

    private static String firstNonNull(String... s){
        for (var x: s) if (x!=null && !x.isBlank()) return x;
        return null;
    }

    private static String reasonIfEmpty(String... tokens){
        for (var t: tokens) if (t!=null && !t.isBlank()) return null;
        return "NO_TOKEN";
    }
}