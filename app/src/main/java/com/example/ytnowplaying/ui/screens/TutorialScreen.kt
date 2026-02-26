package com.example.ytnowplaying.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytnowplaying.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.PaddingValues

// ✅ 여기 SCALE 값만 바꾸면 이 화면 글씨가 “일괄”로 커짐
private const val SCALE = 1.20f
private fun s(baseSp: Float) = (baseSp * SCALE).sp

@Composable
fun TutorialScreen(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F6FA))
    ) {
        // ---- TopBar ----
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "←",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(end = 14.dp, top = 0.dp, bottom = 8.dp)
                )

                Text(
                    text = "관련 영상 덜 보는 법",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111111)
                )
            }
        }

        // ---- Body ----
        // ✅ 변경: 하단 고정 버튼 제거 -> LazyColumn 마지막에 "돌아가기"를 item으로 추가
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // 상단 그라데이션 카드 (네가 쓰는 그대로)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF2563EB), Color(0xFF8A2BE2))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Column {
                            Text(
                                text = "YouTube 알고리즘 제어하기",
                                color = Color.White,
                                fontSize = s(18f),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "원하지 않는 영상을 덜 추천받기 위한 방법을 알려드립니다",
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = s(13f),
                                lineHeight = s(18f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            item {
                StepCard(
                    stepNo = "1",
                    title = "영상에서 메뉴 열기",
                    desc = "YouTube 홈 화면이나 추천 영상 목록에서\n원하지 않는 영상의 점 3개 아이콘(⋮)을 탭하세요.",
                    imageRes = R.drawable.tutorial1
                )
            }

            item {
                StepCard(
                    stepNo = "2",
                    title = "‘관심 없음’ 선택",
                    desc = "메뉴가 열리면 ‘관심 없음’을 선택하세요.",
                    imageRes = R.drawable.tutorial2
                )
            }

            item {
                TipCard(
                    tips = listOf(
                        "자주 사용할수록 YouTube 알고리즘이 여러분의 취향을 더 잘 이해하게 됩니다.",
                        "시청 기록을 삭제하면 관련 추천을 줄일 수 있습니다."
                    )
                )
            }

            // ✅ 추가: 스크롤 마지막에 자연스럽게 "돌아가기"
            item {
                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111827))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "돌아가기",
                        color = Color.White,
                        fontSize = s(16f),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNo: String,
    title: String,
    desc: String,
    imageRes: Int,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 파란 동그라미 번호
                Box(
                    modifier = Modifier
                        .size(30.dp) // ✅ 기존 28.dp (글씨 커져서 약간 키움)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF2563EB)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNo,
                        color = Color.White,
                        fontSize = s(14f), // ✅ 기존 14.sp
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(Modifier.width(10.dp))

                Text(
                    text = title,
                    color = Color(0xFF111111),
                    fontSize = s(16f), // ✅ 기존 16.sp
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = desc,
                color = Color(0xFF6B7280),
                fontSize = s(13.5f), // ✅ 기존 13.5.sp
                lineHeight = s(19f),
                fontWeight = FontWeight.Medium
            )

            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "tutorial step $stepNo image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F4F6)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun TipCard(
    tips: List<String>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F0FF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "💡 추가 팁",
                color = Color(0xFF6D28D9),
                fontSize = s(14.5f), // ✅ 기존 14.5.sp
                fontWeight = FontWeight.ExtraBold
            )

            tips.forEach { t ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• ",
                        color = Color(0xFF6D28D9),
                        fontSize = s(13.5f) // ✅ 기존 13.5.sp
                    )
                    Text(
                        text = t,
                        color = Color(0xFF374151),
                        fontSize = s(13.5f), // ✅ 기존 13.5.sp
                        lineHeight = s(19f),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
