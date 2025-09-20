package com.ms.helloworld.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.ms.helloworld.ui.theme.MainColor


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationPermissionRequester(
    autoRequest: Boolean = true,
    onGranted: () -> Unit
) {
    val context = LocalContext.current

    // Android 13 미만은 권한 필요 없음
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        LaunchedEffect(Unit) { onGranted() }
        return
    }

    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

    // 권한이 허용되면 콜백 1회 호출
    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) onGranted()
    }

    when {
        permissionState.status.isGranted -> Unit

        permissionState.status.shouldShowRationale -> {
            // 사용자가 한 번 거절했을 때: 이유 다이얼로그
            PermissionRationaleDialog(
                onDismiss = { /* no-op */ },
                onRequest = { permissionState.launchPermissionRequest() },
                onOpenSettings = { openAppNotificationSettings(context) }
            )
        }

        else -> {
            // 아직 묻지 않았거나 "다시 묻지 않음"으로 거절됐을 때
            if (autoRequest) {
                LaunchedEffect(Unit) { permissionState.launchPermissionRequest() }
            } else {
                PermissionAskDialog(
                    onAsk = { permissionState.launchPermissionRequest() },
                    onOpenSettings = { openAppNotificationSettings(context) }
                )
            }
        }
    }
}

@Composable
private fun PermissionAskDialog(
    onAsk: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("알림 권한이 필요해요") },
        text = { Text("건강 상태 알림 및 일정 알림을 받기 위해 알림 권한을 허용해주세요.") },
        confirmButton = {
            TextButton(onClick = onAsk) { Text("허용 요청") }
        },
        dismissButton = {
            TextButton(onClick = onOpenSettings) { Text("설정 열기") }
        }
    )
}

@Composable
private fun PermissionRationaleDialog(
    onDismiss: () -> Unit,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "알림 권한이 거부됨",
                style = MaterialTheme.typography.headlineSmall,
                color = MainColor.copy(alpha = 0.9f),
            )
        },
        text = {
            Text(
                text = "심박수, 스트레스, 활동량 등 건강 관련 중요한 알림을 받으려면 알림 권한이 필요합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MainColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(
                    text = "다시 요청",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onOpenSettings,
                border = BorderStroke(1.dp, MainColor.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MainColor.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "설정 열기",
                    fontWeight = FontWeight.Medium
                )
            }
        },
        containerColor = Color.White,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(20.dp)
    )
}

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}