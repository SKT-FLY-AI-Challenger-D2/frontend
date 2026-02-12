package com.example.ytnowplaying.nav

object Routes {
    const val Splash = "splash"

    const val Start = "start"
    const val Permission1 = "permission_step_1"
    const val Permission2 = "permission_step_2"
    const val Intro = "intro"
    const val Main = "main"

    const val ReportHistory = "report_history"

    const val Settings = "settings"

    const val Report = "report"
    const val ReportArgId = "reportId"
    fun report(reportId: String) = "$Report/$reportId"
}
