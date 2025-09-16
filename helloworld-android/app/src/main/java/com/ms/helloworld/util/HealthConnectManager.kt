package com.ms.helloworld.util

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "싸피_HealthConnectManager"
class HealthConnectManager(private val context: Context) {
    val healthConnectClient = HealthConnectClient.getOrCreate(context)

    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    // 권한 상태 확인 함수 추가
    suspend fun checkPermissions(): Map<String, Boolean> {
        val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
        return permissions.associate { permission ->
            permission.toString() to grantedPermissions.contains(permission)
        }
    }

    // 오늘의 시간 범위를 계산하는 함수
    private fun getTodayTimeRange(): Pair<Instant, Instant> {
        val systemZone = ZoneId.systemDefault()
        val today = LocalDate.now()

        // 오늘 00:00:00부터
        val startTime = today.atStartOfDay(systemZone).toInstant()
        // 오늘 23:59:59까지
        val endTime = today.plusDays(1).atStartOfDay(systemZone).toInstant()

        Log.d(TAG, "데이터 조회 시간 범위: ${startTime} ~ ${endTime}")
        Log.d(TAG, "오늘: ${today}")

        return Pair(startTime, endTime)
    }

    suspend fun readHeartRates(): List<HeartRateRecord> {
        val (startTime, endTime) = getTodayTimeRange()

        Log.d(TAG, "심박수 데이터 조회 - 오늘")

        val request = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )

        val result = healthConnectClient.readRecords(request).records
        Log.d(TAG, "심박수 데이터 개수: ${result.size}")
        return result
    }

    suspend fun readStepCounts(): List<StepsRecord> {
        val (startTime, endTime) = getTodayTimeRange()

        Log.d(TAG, "걸음수 데이터 조회 - 오늘")
        Log.d(TAG, "현재 시간대: ${ZoneId.systemDefault()}")

        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )

        val result = healthConnectClient.readRecords(request).records
        Log.d(TAG, "걸음수 레코드 개수: ${result.size}")

        // 각 레코드 상세 정보 로그
        result.forEachIndexed { index, record ->
            Log.d(TAG, "걸음수 레코드 $index: ${record.count}보, 시작시간: ${record.startTime}, 종료시간: ${record.endTime}")
        }

        return result
    }

    suspend fun readCaloriesBurned(): List<TotalCaloriesBurnedRecord> {
        val (startTime, endTime) = getTodayTimeRange()

        Log.d(TAG, "칼로리 데이터 조회 - 오늘")

        val request = ReadRecordsRequest(
            recordType = TotalCaloriesBurnedRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )

        val result = healthConnectClient.readRecords(request).records
        Log.d(TAG, "칼로리 레코드 개수: ${result.size}")
        return result
    }

    suspend fun readDistanceWalked(): List<DistanceRecord> {
        val (startTime, endTime) = getTodayTimeRange()

        val request = ReadRecordsRequest(
            recordType = DistanceRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )

        val result = healthConnectClient.readRecords(request).records
        Log.d(TAG, "거리 레코드 개수: ${result.size}")
        return result
    }

    suspend fun readActiveCaloriesBurned(): List<ActiveCaloriesBurnedRecord> {
        val (startTime, endTime) = getTodayTimeRange()

        val request = ReadRecordsRequest(
            recordType = ActiveCaloriesBurnedRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )

        val result = healthConnectClient.readRecords(request).records
        Log.d(TAG, "활동칼로리 레코드 개수: ${result.size}")
        return result
    }

    suspend fun readSleepSessions(): List<SleepSessionRecord> {
        val (startTime, endTime) = getTodayTimeRange()

        val request = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )

        val result = healthConnectClient.readRecords(request).records
        Log.d(TAG, "수면 레코드 개수: ${result.size}")
        return result
    }

    suspend fun debugConnectionStatus() {
        try {
            Log.d(TAG, "=== 헬스커넥트 연결 상태 디버깅 ===")

            // 1. SDK 상태 확인
            val sdkStatus = HealthConnectClient.getSdkStatus(context)
            Log.d(TAG, "SDK 상태: $sdkStatus")

            // 2. 권한 상태 상세 확인
            val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
            Log.d(TAG, "부여된 권한 개수: ${grantedPermissions.size}")

            permissions.forEach { permission ->
                val isGranted = grantedPermissions.contains(permission)
                Log.d(TAG, "권한 ${permission.toString().substringAfterLast('.')}: $isGranted")
            }

            // 3. 데이터 소스 확인 (어떤 앱에서 데이터를 제공하는지)
            checkDataSources()

        } catch (e: Exception) {
            Log.e(TAG, "연결 상태 확인 중 오류: ${e.message}", e)
        }
    }

    private suspend fun checkDataSources() {
        try {
            val (startTime, endTime) = getTodayTimeRange()

            Log.d(TAG, "=== 데이터 소스 확인 (오늘) ===")

            // 걸음수 데이터 소스 확인
            val stepRequest = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val stepRecords = healthConnectClient.readRecords(stepRequest).records
            Log.d(TAG, "오늘 걸음수 레코드: ${stepRecords.size}개")

            if (stepRecords.isNotEmpty()) {
                // 데이터 소스 정보 출력
                stepRecords.take(5).forEach { record ->
                    Log.d(TAG, "걸음수 레코드: ${record.count}보, 소스: ${record.metadata.dataOrigin.packageName}, 시간: ${record.startTime}")
                }
            } else {
                Log.w(TAG, "오늘 걸음수 데이터가 전혀 없습니다!")
            }

            // 다른 데이터 타입들도 확인
            checkOtherDataTypes(startTime, endTime)

        } catch (e: Exception) {
            Log.e(TAG, "데이터 소스 확인 중 오류: ${e.message}", e)
        }
    }

    private suspend fun checkOtherDataTypes(startTime: Instant, endTime: Instant) {
        // 심박수 확인
        try {
            val heartRateRequest = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val heartRateRecords = healthConnectClient.readRecords(heartRateRequest).records
            Log.d(TAG, "오늘 심박수 레코드: ${heartRateRecords.size}개")
        } catch (e: Exception) {
            Log.e(TAG, "심박수 확인 오류: ${e.message}")
        }

        // 칼로리 확인
        try {
            val caloriesRequest = ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val caloriesRecords = healthConnectClient.readRecords(caloriesRequest).records
            Log.d(TAG, "오늘 칼로리 레코드: ${caloriesRecords.size}개")
        } catch (e: Exception) {
            Log.e(TAG, "칼로리 확인 오류: ${e.message}")
        }
    }
}