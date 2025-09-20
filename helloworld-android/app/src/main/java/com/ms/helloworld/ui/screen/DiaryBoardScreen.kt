package com.ms.helloworld.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

// 사진 타입
enum class PhotoType {
    ULTRASOUND, // 초음파 사진
    REGULAR     // 일반 사진
}

// 사진 데이터
data class DiaryPhoto(
    val id: String,
    val url: String,
    val type: PhotoType
)

// 일기 데이터
data class DiaryBoardData(
    val title: String,
    val content: String,
    val photos: List<DiaryPhoto>,
    val date: String,
    val diaryType: String // "birth" 또는 "observation"
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("NewApi")
@Composable
fun DiaryBoardScreen(
    navController: NavHostController,
    diaryType: String, // "birth" 또는 "observation"
    day: Int,
    pregnancyWeek: Int = 0,
    pregnancyDay: Int = 0
) {
    val backgroundColor = Color(0xFFF5F5F5)
    val title = if (diaryType == "birth") "출산일기" else "관찰일기"

    // 디버깅용 로그
    println("🐛 DiaryBoardScreen - pregnancyWeek: $pregnancyWeek, pregnancyDay: $pregnancyDay")

    // 샘플 데이터
    val diaryData = remember {
        DiaryBoardData(
            title = "My lovely family",
            content = "Today, Sally took care of her cute little sister. She carefully took care of her child I gave the cake to Sally, who took good of her younger sister. I hope that our family will always be healthy and happy in the future.",
            photos = listOf(
                DiaryPhoto("1", "ultrasound_sample", PhotoType.ULTRASOUND),
                DiaryPhoto("2", "regular_sample", PhotoType.REGULAR)
            ),
            date = "2024-01-15",
            diaryType = diaryType
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        // 일기 수정 화면으로 이동
                        navController.navigate("diary_register/$diaryType/$day/true")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "수정"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                    actionIconContentColor = Color.Black
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
        // 사진 섹션
        if (diaryData.photos.isNotEmpty()) {
            item {
                PhotoSection(
                    photos = diaryData.photos,
                    onCharacterGenerateClick = {
                        // 캐리커쳐 생성 화면으로 이동
                        navController.navigate("character_generate")
                    }
                )
            }
        }

        // 텍스트 내용 섹션
        item {
            TextContentSection(
                title = diaryData.title,
                content = diaryData.content
            )
        }

            // 하단 여백
            item {
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}

@Composable
fun PhotoSection(
    photos: List<DiaryPhoto>,
    onCharacterGenerateClick: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(photos) { photo ->
            PhotoItem(
                photo = photo,
                onCharacterGenerateClick = onCharacterGenerateClick
            )
        }
    }
}

@Composable
fun PhotoItem(
    photo: DiaryPhoto,
    onCharacterGenerateClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(280.dp)
    ) {
        // 사진
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // 실제 앱에서는 Coil이나 Glide를 사용해서 URL로 이미지 로드
                // 여기서는 샘플 이미지 표시
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (photo.type == PhotoType.ULTRASOUND) "초음파 사진" else "일반 사진",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                // 초음파 사진인 경우 캐리커쳐 생성 버튼 표시
                if (photo.type == PhotoType.ULTRASOUND) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Button(
                            onClick = onCharacterGenerateClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF49699)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "캐리커쳐 생성",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextContentSection(
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 제목
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // 내용
            Text(
                text = content,
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 22.sp
            )
        }
    }
}

// 실제 이미지 로딩을 위한 Composable (Coil 사용 예시)
@Composable
fun NetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    // 실제 구현에서는 Coil의 AsyncImage를 사용
    // AsyncImage(
    //     model = url,
    //     contentDescription = contentDescription,
    //     modifier = modifier,
    //     contentScale = contentScale,
    //     placeholder = painterResource(R.drawable.placeholder),
    //     error = painterResource(R.drawable.error_image)
    // )

    // 현재는 플레이스홀더만 표시
    Box(
        modifier = modifier.background(Color(0xFFE0E0E0)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "이미지 로딩 중...",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DiaryBoardScreenPreview() {
    DiaryBoardScreen(
        navController = null as NavHostController,
        diaryType = "birth",
        day = 1
    )
}