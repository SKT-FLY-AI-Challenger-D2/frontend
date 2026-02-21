package com.example.ytnowplaying.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.ytnowplaying.data.user.RegisterRequest
import com.example.ytnowplaying.data.user.UserClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onGoLogin: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val userClient = remember { UserClient("http://10.0.2.2:8000/") }

    var name by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("M") }
    var birth by remember { mutableStateOf("") }

    var pwVisible by remember { mutableStateOf(false) }
    var pw2Visible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ✅ 기존(설정 화면)과 동일한 상단 바
        AuthTopBar(title = "회원가입", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.realy_logo),
                contentDescription = null,
                modifier = Modifier.size(54.dp)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "REALY.AI 시작하기",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "계정을 만들어 서비스를 이용하세요",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )

            Spacer(Modifier.height(18.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                LabeledField("이름") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("홍길동") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LabeledField("사용자 ID") {
                    OutlinedTextField(
                        value = userId,
                        onValueChange = { userId = it },
                        placeholder = { Text("사용자 ID 입력") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LabeledField("비밀번호") {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("8자 이상 입력하세요") },
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
                }

                LabeledField("비밀번호 확인") {
                    OutlinedTextField(
                        value = password2,
                        onValueChange = { password2 = it },
                        placeholder = { Text("비밀번호를 다시 입력하세요") },
                        singleLine = true,
                        visualTransformation = if (pw2Visible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                text = if (pw2Visible) "🙈" else "👁",
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .clickable { pw2Visible = !pw2Visible }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LabeledField("이메일") {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("이메일 입력") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text("성별", fontSize = 13.sp, color = Color(0xFF111111))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SexChip("남(M)", selected = sex == "M") { sex = "M" }
                    SexChip("여(F)", selected = sex == "F") { sex = "F" }
                }

                Spacer(Modifier.height(14.dp))

                LabeledField("생년월일") {
                    OutlinedTextField(
                        value = birth,
                        onValueChange = { birth = it },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                GradientWideButton(
                    text = if (loading) "회원가입 중..." else "회원가입",
                    enabled = !loading,
                    onClick = {
                        val n = name.trim()
                        val uid = userId.trim()
                        val pw = password.trim()
                        val pw2 = password2.trim()
                        val em = email.trim()
                        val bd = birth.trim()

                        if (n.isBlank() || uid.isBlank() || pw.isBlank() || pw2.isBlank() || em.isBlank() || bd.isBlank()) {
                            Toast.makeText(ctx, "모든 항목을 입력하세요.", Toast.LENGTH_SHORT).show()
                            return@GradientWideButton
                        }
                        if (pw.length < 8) {
                            Toast.makeText(ctx, "비밀번호는 8자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                            return@GradientWideButton
                        }
                        if (pw != pw2) {
                            Toast.makeText(ctx, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                            return@GradientWideButton
                        }
                        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(bd)) {
                            Toast.makeText(ctx, "생년월일 형식이 올바르지 않습니다. (YYYY-MM-DD)", Toast.LENGTH_SHORT).show()
                            return@GradientWideButton
                        }

                        loading = true
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                userClient.register(
                                    RegisterRequest(
                                        userId = uid,
                                        password = pw,
                                        name = n,
                                        userEmail = em,
                                        sex = sex,
                                        birthDate = bd
                                    )
                                )
                            }
                            loading = false

                            val msg = res.body?.message ?: res.message ?: "회원가입에 실패했습니다."
                            val ok = (res.code in 200..299) && (res.body?.userId != null)

                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                            if (ok) onGoLogin()
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "이미 계정이 있으신가요? ",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280)
                    )
                    Text(
                        text = "로그인",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.clickable { onGoLogin() }
                    )
                }

                Spacer(Modifier.height(20.dp))
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
private fun LabeledField(label: String, field: @Composable () -> Unit) {
    Text(label, fontSize = 13.sp, color = Color(0xFF111111))
    Spacer(Modifier.height(6.dp))
    field()
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SexChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFFEAF2FF) else Color(0xFFF3F4F6)
    val fg = if (selected) Color(0xFF2563EB) else Color(0xFF6B7280)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text = text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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