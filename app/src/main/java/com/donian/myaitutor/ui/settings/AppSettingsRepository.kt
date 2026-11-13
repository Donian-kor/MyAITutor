package com.donian.myaitutor.ui.settings

import android.content.Context

/**
 * 사용자가 설정 화면에서 직접 고르는 값의 저장 계약.
 *
 * 학년([com.donian.myaitutor.ai.TutorGrade])과 달리 Thinking 사용 여부는 학년에 연동되지 않고
 * 사용자가 명시적으로 켜고 끄는 값입니다. 테스트에서는 이 인터페이스를 대역으로 주입합니다.
 */
interface AppSettingsRepository {

    /**
     * Gemma 4 추론(Thinking) 모드 사용 여부.
     * 기본값은 false이며, 학년이 중등·고등이어도 자동으로 켜지지 않습니다.
     */
    var thinkingMode: Boolean
}

/** SharedPreferences 기반 기본 구현. */
class PrefsAppSettingsRepository(context: Context) : AppSettingsRepository {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var thinkingMode: Boolean
        get() = preferences.getBoolean(KEY_THINKING_MODE, false)
        set(value) = preferences.edit().putBoolean(KEY_THINKING_MODE, value).apply()

    private companion object {
        const val PREFS_NAME = "myaitutor_app_settings"
        const val KEY_THINKING_MODE = "thinking_mode"
    }
}