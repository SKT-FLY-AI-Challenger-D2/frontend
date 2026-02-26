package com.example.ytnowplaying.render

interface AlertRenderer {
    /**
     * @param title 오버레이 카드의 상단 제목(예: "! 영상에 문제가 있습니다")
     * @param bodyLead 본문 1번째 줄(선제 요약). null이면 기본 문구 사용.
     */
    fun showWarning(title: String, bodyLead: String? = null, onTap: (() -> Unit)? = null)
    fun clearWarning()
}
