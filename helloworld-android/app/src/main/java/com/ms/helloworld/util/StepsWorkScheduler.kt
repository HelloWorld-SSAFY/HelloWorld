package com.ms.helloworld.util

import android.content.Context
import android.util.Log
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepsWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "StepsWorkScheduler"
        private const val WORK_NAME_12 = "steps_upload_12"
        private const val WORK_NAME_16 = "steps_upload_16"
        private const val WORK_NAME_20 = "steps_upload_20"
    }

    fun scheduleStepsUpload() {
        Log.d(TAG, "걸음수 업로드 스케줄링 시작")

        // 기존 작업들 취소
        WorkManager.getInstance(context).cancelAllWorkByTag("steps_upload")

        // 네트워크 제약 조건
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 12시, 16시, 20시 각각 스케줄
        scheduleWorkAt(12, 0, WORK_NAME_12, constraints)
        scheduleWorkAt(16, 0, WORK_NAME_16, constraints)
        scheduleWorkAt(20, 0, WORK_NAME_20, constraints)

        Log.d(TAG, "모든 걸음수 업로드 작업 스케줄 완료")
    }

    private fun scheduleWorkAt(hour: Int, minute: Int, workName: String, constraints: Constraints) {
        val targetTime = LocalTime.of(hour, minute)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        var targetDateTime = today.atTime(targetTime)

        // 만약 오늘 해당 시간이 이미 지났다면 내일로 설정
        if (targetDateTime.isBefore(now) || targetDateTime.isEqual(now)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val delayInMinutes = ChronoUnit.MINUTES.between(now, targetDateTime)

        Log.d(TAG, "${hour}시 작업 스케줄: 현재 시간에서 ${delayInMinutes}분 후 실행 (${targetDateTime})")

        // 매일 반복되는 작업 생성
        val periodicWorkRequest = PeriodicWorkRequestBuilder<StepsUploadWorker>(
            24, TimeUnit.HOURS // 24시간마다 반복
        )
            .setConstraints(constraints)
            .setInitialDelay(delayInMinutes, TimeUnit.MINUTES)
            .addTag("steps_upload")
            .build()

        // 고유한 작업으로 예약 (중복 방지)
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicWorkRequest
            )
    }

    fun cancelAllStepsWork() {
        WorkManager.getInstance(context).cancelAllWorkByTag("steps_upload")
        Log.d(TAG, "모든 걸음수 업로드 작업 취소")
    }
}