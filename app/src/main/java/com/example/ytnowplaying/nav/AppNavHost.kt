package com.example.ytnowplaying.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ytnowplaying.AppContainer
import com.example.ytnowplaying.ui.screens.IntroScreen
import com.example.ytnowplaying.ui.screens.MainScreen
import com.example.ytnowplaying.ui.screens.PermissionStep1Screen
import com.example.ytnowplaying.ui.screens.PermissionStep2Screen
import com.example.ytnowplaying.ui.screens.ReportHistoryScreen
import com.example.ytnowplaying.ui.screens.ReportScreen
import com.example.ytnowplaying.ui.screens.StartScreen

@Composable
fun AppNavHost(
    initialOpenReportId: String?,
    initialFromOverlay: Boolean,
    initialAlertText: String?,
) {
    val navController = rememberNavController()

    // ✅ NavHost의 startDestination은 “첫 구성 시점”에만 결정하고 이후엔 바꾸지 않는 게 안전함.
    //    - cold start: reportId가 있으면 Report로 바로 시작
    //    - warm start(onNewIntent): 아래 LaunchedEffect가 Report로 이동
    val startDestination = rememberSaveable {
        val id = initialOpenReportId?.takeIf { it.isNotBlank() }
        if (id != null) Routes.report(id) else Routes.Start
    }

    // ✅ 중복 네비게이션 방지용(특히 onNewIntent 연속 호출/재구성 시)
    val lastHandledReportId = rememberSaveable { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.Start) {
            StartScreen(onStart = { navController.navigate(Routes.Permission1) })
        }

        composable(Routes.Permission1) {
            PermissionStep1Screen(
                onGranted = {
                    navController.navigate(Routes.Permission2) {
                        popUpTo(Routes.Permission1) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Permission2) {
            PermissionStep2Screen(
                onGranted = {
                    navController.navigate(Routes.Intro) {
                        popUpTo(Routes.Permission2) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Intro) {
            IntroScreen(
                onNext = {
                    navController.navigate(Routes.Main) {
                        popUpTo(Routes.Intro) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Main) {
            MainScreen(
                onOpenHistory = { navController.navigate(Routes.ReportHistory) },
                onOpenReport = { reportId -> navController.navigate(Routes.report(reportId)) }
            )
        }

        composable(Routes.ReportHistory) {
            ReportHistoryScreen(
                repo = AppContainer.reportRepository,
                onOpenReport = { reportId -> navController.navigate(Routes.report(reportId)) }
            )
        }

        composable(
            route = "${Routes.Report}/{${Routes.ReportArgId}}",
            arguments = listOf(navArgument(Routes.ReportArgId) { type = NavType.StringType })
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString(Routes.ReportArgId).orEmpty()
            ReportScreen(
                repo = AppContainer.reportRepository,
                reportId = reportId,
                launchedFromOverlay = initialFromOverlay,
                alertText = initialAlertText,
                onBack = { navController.popBackStack() }
            )
        }
    }

    // ✅ 오버레이/Intent로 reportId가 들어오면 언제든 Report로 강제 이동
    LaunchedEffect(initialOpenReportId, initialFromOverlay) {
        val id = initialOpenReportId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (lastHandledReportId.value == id) return@LaunchedEffect
        lastHandledReportId.value = id

        navController.navigate(Routes.report(id)) {
            launchSingleTop = true

            // 오버레이에서 들어온 경우, 뒤로가기 시 Start로 돌아가는 걸 원천 차단하고 싶으면 유지
            if (initialFromOverlay) {
                popUpTo(Routes.Start) { inclusive = true }
            }
        }
    }
}
