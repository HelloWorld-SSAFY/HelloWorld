package com.ms.helloworld.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ms.helloworld.repository.StepsRepository

class StepsUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StepsUploadWorkerEntryPoint {
        fun stepsRepository(): StepsRepository
        fun locationManager(): LocationManager
    }

    companion object {
        private const val TAG = "StepsUploadWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "걸음수 업로드 작업 시작")

            // Hilt EntryPoint를 통해 의존성 가져오기
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                StepsUploadWorkerEntryPoint::class.java
            )

            val stepsRepository = entryPoint.stepsRepository()
            val locationManager = entryPoint.locationManager()

            val location = locationManager.getCurrentLocation()
            if (location != null) {
                val result = stepsRepository.submitStepsData(location.first, location.second)

                if (result.isSuccess) {
                    Log.d(TAG, "걸음수 데이터 전송 성공")
                    Result.success()
                } else {
                    Log.e(TAG, "걸음수 데이터 전송 실패: ${result.exceptionOrNull()?.message}")
                    Result.retry()
                }
            } else {
                Log.e(TAG, "위치 정보를 가져올 수 없습니다")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "걸음수 업로드 작업 중 오류: ${e.message}", e)
            Result.retry()
        }
    }
}