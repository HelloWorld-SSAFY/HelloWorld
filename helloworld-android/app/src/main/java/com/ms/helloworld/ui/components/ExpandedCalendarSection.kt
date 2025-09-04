package com.ms.helloworld.ui.components

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
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExpandedCalendarSection(
    onCollapse: () -> Unit,
    onDateClick: (String) -> Unit = {},
    postsMap: Map<String, List<Any>> = emptyMap()
) {
    val currentCalendar = Calendar.getInstance()
    val initialCalendar = Calendar.getInstance().apply { time = currentCalendar.time }
    var displayCalendar by remember { mutableStateOf(initialCalendar) }
    var selectedDate by remember { mutableStateOf(currentCalendar.get(Calendar.DAY_OF_MONTH).toString()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 헤더 - 일주일 캘린더와 동일한 구조
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
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 캘린더 그리드
            val monthCalendar = Calendar.getInstance().apply {
                time = displayCalendar.time
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val firstDayOfWeek = monthCalendar.get(Calendar.DAY_OF_WEEK) - 1 // 0=일요일
            val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            val weeks = mutableListOf<List<Calendar?>>()
            var currentWeek = mutableListOf<Calendar?>()
            
            // 첫 주의 전월 날짜들
            val prevMonth = Calendar.getInstance().apply {
                time = displayCalendar.time
                add(Calendar.MONTH, -1)
                val lastDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, lastDay - firstDayOfWeek + 1)
            }
            
            repeat(firstDayOfWeek) {
                currentWeek.add(Calendar.getInstance().apply {
                    time = prevMonth.time
                    add(Calendar.DAY_OF_MONTH, it)
                })
            }
            
            // 현재 월의 모든 날짜들
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
            
            // 마지막 주의 다음달 날짜들
            if (currentWeek.isNotEmpty()) {
                val nextMonth = Calendar.getInstance().apply {
                    time = displayCalendar.time
                    add(Calendar.MONTH, 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                var nextMonthDay = 0
                while (currentWeek.size < 7) {
                    currentWeek.add(Calendar.getInstance().apply {
                        time = nextMonth.time
                        add(Calendar.DAY_OF_MONTH, nextMonthDay++)
                    })
                }
                weeks.add(currentWeek.toList())
            }
            
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    week.forEach { date ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.height(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                            if (date != null) {
                                val dateString = date.get(Calendar.DAY_OF_MONTH).toString()
                                val isSelected = dateString == selectedDate && 
                                               date.get(Calendar.MONTH) == displayCalendar.get(Calendar.MONTH)
                                val isToday = date.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) && 
                                             date.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH) && 
                                             date.get(Calendar.DAY_OF_MONTH) == currentCalendar.get(Calendar.DAY_OF_MONTH)
                                val isCurrentMonth = date.get(Calendar.MONTH) == displayCalendar.get(Calendar.MONTH)
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
                                            indication = rememberRipple()
                                        ) { 
                                            if (isCurrentMonth) {
                                                selectedDate = dateString
                                                onDateClick(dateKey)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = dateString,
                                            fontSize = 14.sp,
                                            color = when {
                                                isSelected -> Color.White
                                                !isCurrentMonth -> Color.Gray.copy(alpha = 0.5f)
                                                else -> Color.Black
                                            },
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
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 축소 버튼
            Text(
                "∧",
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple()
                ) { onCollapse() }
            )
        }
    }
}