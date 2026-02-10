package com.example.ytnowplaying.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ytnowplaying.AppContainer
import com.example.ytnowplaying.permissions.PermissionChecker
import com.example.ytnowplaying.prefs.OnboardingPrefs
import com.example.ytnowplaying.ui.screens.IntroScreen
import com.example.ytnowplaying.ui.screens.MainScreen
import com.example.ytnowplaying.ui.screens.PermissionStep1Screen
import com.example.ytnowplaying.ui.screens.PermissionStep2Screen
import com.example.ytnowplaying.ui.screens.ReportHistoryScreen
import com.example.ytnowplaying.ui.screens.ReportScreen
import com.example.ytnowplaying.ui.screens.SplashScreen
import com.example.ytnowplaying.ui.screens.StartScreen
import kotlinx.coroutines.delay

@Composable
fun AppNavHost(
    initialOpenReportId: String?,
    initialFromOverlay: Boolean,
    initialAlertText: String?,
) {
    val navController = rememberNavController()

    // startDestination은 첫 구성 시점에만 결정
    val startDestination = rememberSaveable {
        val id = initialOpenReportId?.takeIf { it.isNotBlank() }
        if (id != null) Routes.report(id) else Routes.Splash
    }

    val lastHandledReportId = rememberSaveable { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.Splash) {
            val ctx = LocalContext.current

            // 로딩 화면을 항상 1번 보여주고 여기서 다음 화면 결정
            LaunchedEffect(Unit) {
                // 너무 짧으면 Splash가 안 보일 수 있어 최소 지연(원하면 조절)
                delay(500L) // 조절 가능

                val next = if (!OnboardingPrefs.isDone(ctx)) {
                    Routes.Start
                } else {
                    when {
                        !PermissionChecker.hasNotificationListenerAccess(ctx) -> Routes.Permission1
                        !PermissionChecker.hasOverlayPermission(ctx) -> Routes.Permission2
                        else -> Routes.Main
                    }
                }

                navController.navigate(next) {
                    popUpTo(Routes.Splash) { inclusive = true }
                    launchSingleTop = true
                }
            }

            SplashScreen()
        }

        composable(Routes.Start) {
            StartScreen(
                onStart = {
                    navController.navigate(Routes.Permission1) {
                        // Start로 뒤로가기 복귀 방지(“튜토리얼 후 Start 재진입” 원천 차단)
                        popUpTo(Routes.Start) { inclusive = true }
                    }
                }
            )
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
            val ctx = LocalContext.current
            IntroScreen(
                onNext = {
                    // 튜토리얼 완료 플래그 저장
                    OnboardingPrefs.setDone(ctx, true)

                    navController.navigate(Routes.Main) {
                        popUpTo(Routes.Intro) { inclusive = true }
                        launchSingleTop = true
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

    // 오버레이/Intent로 reportId가 들어오면 언제든 Report로 강제 이동
    LaunchedEffect(initialOpenReportId, initialFromOverlay) {
        val id = initialOpenReportId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (lastHandledReportId.value == id) return@LaunchedEffect
        lastHandledReportId.value = id

        navController.navigate(Routes.report(id)) {
            launchSingleTop = true
            if (initialFromOverlay) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }
}
