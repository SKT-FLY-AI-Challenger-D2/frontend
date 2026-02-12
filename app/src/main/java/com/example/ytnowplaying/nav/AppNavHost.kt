package com.example.ytnowplaying.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.ytnowplaying.ui.screens.SettingsScreen
import com.example.ytnowplaying.ui.screens.TutorialScreen
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

    val initialId = initialOpenReportId?.takeIf { it.isNotBlank() }

    // ✅ startDestination은 "항상 고정" (인자 채운 route를 startDestination으로 쓰면 cold start에서 인자 전달이 깨짐)
    val startDestination = Routes.Splash

    // ✅ 중복 navigate 방지 (saveable로 복원하지 않음)
    val lastHandledReportId = remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.Splash) {
            val ctx = LocalContext.current

            LaunchedEffect(Unit) {
                // ✅ 오버레이로 report를 열어야 하는 경우 Splash 자동 네비게이션 금지
                if (!initialOpenReportId.isNullOrBlank()) return@LaunchedEffect

                delay(500L)
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
            val ctx = LocalContext.current
            IntroScreen(
                onNext = {
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
                onOpenReport = { reportId -> navController.navigate(Routes.report(reportId)) },
                onOpenSettings = { navController.navigate(Routes.Settings) }
            )
        }

        composable(Routes.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
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

            // ✅ 이 reportId가 '오버레이로 전달된 reportId'와 같을 때만 overlay로 간주
            val launchedFromOverlayThis = (initialFromOverlay && initialOpenReportId == reportId)
            val alertTextThis = if (launchedFromOverlayThis) initialAlertText else null

            ReportScreen(
                repo = AppContainer.reportRepository,
                reportId = reportId,
                launchedFromOverlay = launchedFromOverlayThis,
                alertText = alertTextThis,
                onOpenTutorial = { navController.navigate(Routes.Tutorial) },
                onBack = { navController.popBackStack() }
            )
        }


        composable(Routes.Tutorial) {
            TutorialScreen(onBack = { navController.popBackStack() })
        }
    }

    // ✅ warm start: onNewIntent로 reportId가 바뀌면 Report로 이동
    LaunchedEffect(initialOpenReportId, initialFromOverlay) {
        val id = initialOpenReportId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (lastHandledReportId.value == id) return@LaunchedEffect
        lastHandledReportId.value = id

        navController.navigate(Routes.report(id)) {
            launchSingleTop = true
            // ❌ findStartDestination/popUpTo 사용 금지 (이번 크래시 원인)
        }
    }
}
