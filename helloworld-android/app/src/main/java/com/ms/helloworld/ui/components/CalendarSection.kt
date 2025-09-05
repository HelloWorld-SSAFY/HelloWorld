package com.ms.helloworld.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarSection(
    onDateClick: (String) -> Unit = {},
    postsMap: Map<String, List<Any>> = emptyMap()
) {
    val calendar = Calendar.getInstance()
    val currentYear = calendar.get(Calendar.YEAR)
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
    
    // 현재 날짜를 기준으로 일주일 날짜 생성
    val weekDates = mutableListOf<Calendar>()
    val startOfWeek = Calendar.getInstance().apply {
        time = calendar.time
        val dayOfWeek = get(Calendar.DAY_OF_WEEK)
        add(Calendar.DAY_OF_MONTH, -(dayOfWeek - Calendar.SUNDAY))
    }
    
    for (i in 0..6) {
        val date = Calendar.getInstance().apply {
            time = startOfWeek.time
            add(Calendar.DAY_OF_MONTH, i)
        }
        weekDates.add(date)
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            // 헤더 - ExpandedCalendarSection과 완전히 동일한 구조
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { }, // 클릭 비활성화
                    enabled = false
                ) {
                    Text(
                        "<", 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold,
                        color = Color.Transparent
                    )
                }
                
                Text(
                    SimpleDateFormat("yyyy. MM", Locale.getDefault()).format(calendar.time),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = { }, // 클릭 비활성화
                    enabled = false
                ) {
                    Text(
                        ">", 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold,
                        color = Color.Transparent
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 캘린더 날짜들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val dayLabels = listOf("일", "월", "화", "수", "목", "금", "토")

                weekDates.forEachIndexed { index, date ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            dayLabels[index],
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val dateString = date.get(Calendar.DAY_OF_MONTH).toString()
                        val isSelected = false
                        val isToday = date.get(Calendar.YEAR) == currentYear && 
                                     date.get(Calendar.MONTH) == currentMonth && 
                                     date.get(Calendar.DAY_OF_MONTH) == currentDay
                        val isCurrentMonth = date.get(Calendar.MONTH) == currentMonth
                        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.time)
                        val hasPost = postsMap[dateKey]?.isNotEmpty() == true
                        
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> Color.Black
                                        isToday -> Color.Gray.copy(alpha = 0.3f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = LocalIndication.current
                                ) { 
                                    onDateClick(dateKey)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    dateString,
                                    color = when {
                                        isSelected -> Color.White
                                        !isCurrentMonth -> Color.Gray.copy(alpha = 0.5f)
                                        else -> Color.Black
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                                if (hasPost && isCurrentMonth) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(
                                                if (isSelected) Color.White else Color.Blue,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
fun FullCalendarContent(
    currentCalendar: Calendar,
    selectedDate: String,
    onDateSelected: (Calendar) -> Unit,
    onDismiss: () -> Unit
) {
    val initialCalendar = Calendar.getInstance().apply { time = currentCalendar.time }
    var displayCalendar by remember { mutableStateOf(initialCalendar) }
    
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
            // 헤더 (월/년도 및 네비게이션)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    displayCalendar = Calendar.getInstance().apply {
                        time = displayCalendar.time
                        add(Calendar.MONTH, -1)
                    }
                }) {
                    Text("<", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                
                Text(
                    SimpleDateFormat("yyyy. MM", Locale.getDefault()).format(displayCalendar.time),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = { 
                    displayCalendar = Calendar.getInstance().apply {
                        time = displayCalendar.time
                        add(Calendar.MONTH, 1)
                    }
                }) {
                    Text(">", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 요일 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val dayHeaders = listOf("일", "월", "화", "수", "목", "금", "토")
                dayHeaders.forEach { day ->
                    Text(
                        text = day,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 캘린더 그리드
            val monthCalendar = Calendar.getInstance().apply {
                time = displayCalendar.time
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val firstDayOfWeek = monthCalendar.get(Calendar.DAY_OF_WEEK) - 1 // 0=일요일
            val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            val weeks = mutableListOf<List<Calendar?>>()
            var currentWeek = mutableListOf<Calendar?>()
            
            // 첫 주의 빈 날짜들
            repeat(firstDayOfWeek) {
                currentWeek.add(null)
            }
            
            // 월의 모든 날짜들
            for (day in 1..daysInMonth) {
                val dayCalendar = Calendar.getInstance().apply {
                    time = displayCalendar.time
                    set(Calendar.DAY_OF_MONTH, day)
                }
                currentWeek.add(dayCalendar)
                if (currentWeek.size == 7) {
                    weeks.add(currentWeek.toList())
                    currentWeek.clear()
                }
            }
            
            // 마지막 주의 빈 날짜들
            if (currentWeek.isNotEmpty()) {
                while (currentWeek.size < 7) {
                    currentWeek.add(null)
                }
                weeks.add(currentWeek.toList())
            }
            
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    week.forEach { date ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (date != null) {
                                val dateString = date.get(Calendar.DAY_OF_MONTH).toString()
                                val isSelected = dateString == selectedDate && 
                                               date.get(Calendar.MONTH) == displayCalendar.get(Calendar.MONTH)
                                val isToday = date.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) && 
                                             date.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH) && 
                                             date.get(Calendar.DAY_OF_MONTH) == currentCalendar.get(Calendar.DAY_OF_MONTH)
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> Color.Black
                                                isToday -> Color.Gray.copy(alpha = 0.3f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = LocalIndication.current
                                        ) { onDateSelected(date) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dateString,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color.White else Color.Black,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 닫기 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("닫기")
                }
            }
        }
}