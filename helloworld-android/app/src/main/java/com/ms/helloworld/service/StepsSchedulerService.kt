package com.ms.helloworld.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ms.helloworld.repository.StepsRepository
import com.ms.helloworld.util.LocationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StepsSchedulerService : Service() {

    @Inject
    lateinit var stepsRepository: StepsRepository

    @Inject
    lateinit var locationManager: LocationManager

    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "steps_scheduler_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification()) // Notification 객체 추가
        handler = Handler(Looper.getMainLooper())
        setupScheduledTask()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "걸음수 수집 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "헬스 데이터를 주기적으로 전송합니다"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("걸음수 수집 중")
            .setContentText("하루 3회 걸음수 데이터를 전송합니다")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // 기본 아이콘 사용
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    private fun setupScheduledTask() {
        runnable = object : Runnable {
            override fun run() {
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

                // 12시, 16시, 20시에만 실행
                if (currentHour == 12 || currentHour == 16 || currentHour == 20) {
                    val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
                    if (currentMinute == 0) { // 정각에만 실행
                        submitStepsData()
                    }
                }

                // 1분마다 체크
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(runnable)
    }

    private fun submitStepsData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val location = locationManager.getCurrentLocation()
                if (location != null) {
                    val result = stepsRepository.submitStepsData(location.first, location.second)
                    if (result.isSuccess) {
                        Log.d("StepsScheduler", "걸음수 데이터 전송 완료: ${Calendar.getInstance().time}")
                    } else {
                        Log.e("StepsScheduler", "걸음수 데이터 전송 실패: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Log.e("StepsScheduler", "위치 정보를 가져올 수 없습니다.")
                }
            } catch (e: Exception) {
                Log.e("StepsScheduler", "걸음수 데이터 전송 실패: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        stopForeground(true)
    }
}