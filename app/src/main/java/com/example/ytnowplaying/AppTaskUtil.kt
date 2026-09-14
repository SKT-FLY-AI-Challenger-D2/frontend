package com.example.ytnowplaying

import android.app.ActivityManager
import android.content.Context

/**
 * 지금 이 순간 앱 자신의 태스크가 "최근 앱" 목록에 존재하는지 직접 조회한다.
 * 명령·이벤트·플래그로 상태를 재구성하지 않고, 매번 실제 상태를 다시 묻는다
 * (`docs/코엑스용 디버깅/5. 오버레이 버튼 생명주기 수정 계획.md` §6.2 F0 참조).
 *
 * 별도 권한 없이 자기 앱 태스크 조회만 하므로 호출측 예외 처리는 불필요하지만,
 * 조회 자체가 실패하면 "태스크 없음(=제거된 것으로 간주)"으로 안전하게 처리한다.
 */
fun isAppTaskAlive(context: Context): Boolean {
    return try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        activityManager.appTasks.any { task ->
            task.taskInfo?.baseActivity?.packageName == context.packageName
        }
    } catch (t: Throwable) {
        false
    }
}
