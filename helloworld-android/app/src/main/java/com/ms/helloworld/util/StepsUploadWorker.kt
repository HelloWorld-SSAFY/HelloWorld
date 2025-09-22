package com.ms.helloworld.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ms.helloworld.repository.StepsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class StepsUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val stepsRepository: StepsRepository,
    private val locationManager: LocationManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "StepsUploadWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "걸음수 업로드 작업 시작")

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