package HealthData;

import com.example.helloworld.healthserver.alarm.service.FcmService;
import com.example.helloworld.healthserver.client.AiServerClient;
import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.dto.HealthDtos;
import com.example.helloworld.healthserver.persistence.HealthDataRepository;
import com.example.helloworld.healthserver.service.HealthDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.helloworld.healthserver.entity.HealthData;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito 기능을 사용하기 위한 설정
class HealthDataServiceTest {

    @InjectMocks // @Mock으로 만들어진 가짜 객체들을 주입받을 실제 테스트 대상
    private HealthDataService healthDataService;

    @Mock // 가짜(Mock) 객체로 만들 의존성
    private HealthDataRepository healthDataRepository;

    @Mock // 가짜(Mock) 객체로 만들 의존성
    private AiServerClient aiServerClient;

    @Mock // 가짜(Mock) 객체로 만들 의존성
    private FcmService fcmService;

    // ... 테스트 메소드들 ...
    // HealthDataServiceTest 클래스 내부에 추가

    @Test
    @DisplayName("정상 상황: AI 서버가 'normal' 모드를 반환하면 FCM 알림을 보내지 않는다")
    void createAndCheck_NormalMode_ShouldNotSendFcm() {
        // given (준비)
        // 1. 테스트용 요청 데이터 준비
        UserPrincipal testUser = new UserPrincipal(100L, 1L);
        HealthDtos.CreateRequest request = new HealthDtos.CreateRequest(null, 0.2, 80);

        // 2. AI 서버가 'normal' 응답을 주도록 설정
        AiServerClient.AnomalyResponse normalResponse = new AiServerClient.AnomalyResponse(
                true, false, "low", "normal", null, null, null, null, null
        );
        when(aiServerClient.checkTelemetry(any(AiServerClient.TelemetryRequest.class)))
                .thenReturn(normalResponse);

        // when (실행)
        AiServerClient.AnomalyResponse actualResponse = healthDataService.createAndCheckHealthData(testUser, request);

        // then (검증)
        // 1. DB 저장 메소드가 1번 호출되었는지 확인
        verify(healthDataRepository, times(1)).save(any(HealthData.class));

        // 2. AI 서버 호출이 1번 되었는지 확인
        verify(aiServerClient, times(1)).checkTelemetry(any(AiServerClient.TelemetryRequest.class));

        // 3. FCM 서비스는 호출되지 않았는지 확인 (가장 중요!)
        verify(fcmService, never()).sendEmergencyNotification(any(), any());

        // 4. 서비스가 AI 서버의 응답을 그대로 반환했는지 확인
        assertThat(actualResponse).isEqualTo(normalResponse);
        assertThat(actualResponse.mode()).isEqualTo("normal");
    }

    @Test
    @DisplayName("위험 상황: AI 서버가 'emergency' 모드를 반환하면 FCM 알림을 보낸다")
    void createAndCheck_EmergencyMode_ShouldSendFcm() {
        // given (준비)
        UserPrincipal testUser = new UserPrincipal(100L, 1L);
        HealthDtos.CreateRequest request = new HealthDtos.CreateRequest(null, 0.8, 130);

        // AI 서버가 'emergency' 응답을 주도록 설정
        AiServerClient.AnomalyResponse emergencyResponse = new AiServerClient.AnomalyResponse(
                true, true, "critical", "emergency", null, null, null, null, null
        );
        when(aiServerClient.checkTelemetry(any(AiServerClient.TelemetryRequest.class)))
                .thenReturn(emergencyResponse);

        // when (실행)
        healthDataService.createAndCheckHealthData(testUser, request);

        // then (검증)
        // 1. DB 저장과 AI 서버 호출 확인
        verify(healthDataRepository, times(1)).save(any(HealthData.class));
        verify(aiServerClient, times(1)).checkTelemetry(any());

        // 2. FCM 서비스가 정확히 1번 호출되었는지 확인 (가장 중요!)
        verify(fcmService, times(1)).sendEmergencyNotification(anyLong(), anyInt());

        // 3. FCM 서비스에 올바른 인자가 전달되었는지 상세 검증 (선택적이지만 매우 유용)
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> heartrateCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(fcmService).sendEmergencyNotification(userIdCaptor.capture(), heartrateCaptor.capture());

        assertThat(userIdCaptor.getValue()).isEqualTo(100L); // testUser의 ID
        assertThat(heartrateCaptor.getValue()).isEqualTo(130); // request의 심박수
    }
}
