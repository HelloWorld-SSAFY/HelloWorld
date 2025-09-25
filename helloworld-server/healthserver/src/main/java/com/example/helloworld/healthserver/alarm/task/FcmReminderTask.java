package com.example.helloworld.healthserver.alarm.task;


import com.example.helloworld.healthserver.alarm.dto.CalendarEventMessage;
import com.example.helloworld.healthserver.alarm.service.FcmService;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FcmReminderTask {

    private final FcmService fcmService;

    /**
     * db-scheduler가 실행할 "FCM 일정 알림" 작업을 정의합니다.
     */
    @Bean
    public Task<CalendarEventMessage> fcmReminderTaskBean() {
        return Tasks.oneTime("fcm-reminder-task", CalendarEventMessage.class)
                .execute((instance, context) -> {
                    // 스케줄러가 실행하는 실제 로직
                    CalendarEventMessage data = instance.getData();
                    fcmService.sendReminderNotification(data.userId(), data.title(), data.body());
                });
    }
}