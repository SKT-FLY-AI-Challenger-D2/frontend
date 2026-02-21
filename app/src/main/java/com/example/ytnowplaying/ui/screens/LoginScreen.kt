package com.example.ytnowplaying.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytnowplaying.R
import com.example.ytnowplaying.data.user.UserClient
import com.example.ytnowplaying.prefs.AuthPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onOpenRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val userClient = remember { UserClient("http://10.0.2.2:8000/") }

    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pwVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ✅ 기존(설정 화면)과 동일한 상단 바
        AuthTopBar(title = "로그인", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))

            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.realy_logo),
                contentDescription = null,
                modifier = Modifier.size(54.dp)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "REALY.AI",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "영상 분석 AI 서비스",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )

            Spacer(Modifier.height(22.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("사용자 ID", fontSize = 13.sp, color = Color(0xFF111111))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    placeholder = { Text("사용자 ID를 입력하세요") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                Text("비밀번호", fontSize = 13.sp, color = Color(0xFF111111))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("비밀번호를 입력하세요") },
                    singleLine = true,
                    visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (pwVisible) "🙈" else "👁",
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .clickable { pwVisible = !pwVisible }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                GradientWideButton(
                    text = if (loading) "로그인 중..." else "로그인",
                    enabled = !loading,
                    onClick = {
                        val uid = userId.trim()
                        val pw = password.trim()
                        if (uid.isBlank() || pw.isBlank()) {
                            Toast.makeText(ctx, "ID/비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                            return@GradientWideButton
                        }

                        loading = true
                        scope.launch {
                            val res = withContext(Dispatchers.IO) { userClient.login(uid, pw) }
                            loading = false

                            val body = res.body
                            val ok = body?.success == true
                            val msg = body?.message ?: res.message ?: "로그인에 실패했습니다."

                            if (ok) {
                                AuthPrefs.setLoggedIn(
                                    userId = body?.userId ?: uid,
                                    name = body?.name,
                                    email = body?.userEmail
                                )
                                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } else {
                                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color(0xFFE5E7EB))
                    )
                    Text(
                        text = "  또는  ",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color(0xFFE5E7EB))
                    )
                }

                Spacer(Modifier.height(14.dp))

                OutlinedWhiteButton(
                    text = "회원가입",
                    onClick = onOpenRegister
                )
            }
        }
    }
}

@Composable
private fun AuthTopBar(
    title: String,
    onBack: () -> Unit
) {
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
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111)
            )
        }
    }
}

@Composable
private fun GradientWideButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val bg = if (enabled) {
        Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF8A2BE2)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF9CA3AF), Color(0xFF9CA3AF)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlinedWhiteButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color(0xFF111111), fontWeight = FontWeight.SemiBold)
    }
}