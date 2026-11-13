package com.donian.myaitutor.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.donian.myaitutor.ai.GemmaAssistant
import com.donian.myaitutor.tutor.RealGemmaTutorEngine

/**
 * [HomeViewModel]의 수명 주기를 담당하는 호스트.
 *
 * GemmaAssistant 인스턴스는 여기서 한 번만 만들고 HomeViewModel과
 * RealGemmaTutorEngine이 같은 인스턴스를 공유한다. Engine/Conversation 중복 생성은 없다.
 *
 * Activity가 회전해도 ViewModelStore에 남으므로 대화/입력 상태가 유지되고,
 * Activity가 종료될 때만 [HomeViewModel.close]가 호출되어 엔진 자원을 해제한다.
 */
class HomeViewModelHost(application: Application) : AndroidViewModel(application) {

    private val assistant = GemmaAssistant(application.applicationContext)

    val homeViewModel: HomeViewModel = HomeViewModel(
        appContext = application.applicationContext,
        assistant = assistant,
        scope = viewModelScope,
        tutorEngine = RealGemmaTutorEngine(assistant)
    )

    override fun onCleared() {
        homeViewModel.close()
    }
}
