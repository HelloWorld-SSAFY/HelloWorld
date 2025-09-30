package com.ms.helloworld.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ms.helloworld.R
import com.ms.helloworld.dto.response.CalendarEventResponse
import com.ms.helloworld.ui.theme.MainColor
import com.ms.helloworld.ui.theme.SubColor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailBottomSheet(
    event: CalendarEventResponse,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        Color.Gray.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp)
                    )
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp)
        ) {
            // 헤더 (제목과 닫기 버튼)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 날짜 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "날짜",
                    modifier = Modifier.size(20.dp),
                    tint = MainColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                val dateFormat = try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .parse(event.startAt.substring(0, 10))
                    SimpleDateFormat("yyyy년 M월 d일", Locale.getDefault()).format(date)
                } catch (e: Exception) {
                    "날짜 정보 없음"
                }
                Text(
                    text = dateFormat,
                    fontSize = 16.sp,
                    color = Color(0xFF333333),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 시간 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_time),
                    contentDescription = "시간",
                    modifier = Modifier.size(20.dp),
                    tint = MainColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                val timeFormat = try {
                    val startTime = event.startAt.substring(11, 16)
                    val endTime = event.endAt?.substring(11, 16)
                    if (endTime != null) "$startTime - $endTime" else startTime
                } catch (e: Exception) {
                    "시간 정보 없음"
                }
                Text(
                    text = timeFormat,
                    fontSize = 16.sp,
                    color = Color(0xFF333333),
                    fontWeight = FontWeight.Medium
                )
            }

            // 메모가 있는 경우
            if (!event.memo.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "메모",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = event.memo,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 14.sp,
                        color = Color(0xFF333333),
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 알림 설정 정보
            if (event.remind) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MainColor.copy(alpha = 0.3f)),
                        shape = CircleShape
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_noti),
                            contentDescription = "알림 아이콘",
                            modifier = Modifier
                                .size(28.dp)
                                .padding(6.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "알림 설정됨",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 액션 버튼들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 수정 버튼
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SubColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "수정",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "수정",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 삭제 버튼
                Button(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF8A95),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "삭제",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "삭제",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = "일정 삭제",
                    modifier = Modifier.size(width = 200.dp, height = 30.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("정말로 이 일정을 삭제하시겠습니까?\n삭제된 일정은 복구할 수 없습니다.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF8A95),
                        contentColor = Color.White
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text("취소", color = Color.Gray)
                }
            }
        )
    }
}