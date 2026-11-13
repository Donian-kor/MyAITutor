package com.donian.myaitutor.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.donian.myaitutor.model.ModelRuntimeCoordinator
import com.donian.myaitutor.model.ModelSelectionRepository
import com.donian.myaitutor.model.PrefsModelSelectionRepository
import com.donian.myaitutor.tutor.SwitchableTutorEngine

/**
 * [HomeViewModel]의 수명 주기를 담당하는 호스트.
 *
 * Gemma/Qwen 어느 쪽이 선택되어도 [ModelRuntimeCoordinator]가 엔진을 소유하며,
 * 메모리에 로드되는 LLM Engine은 항상 1개다. HomeViewModel은 coordinator를 직접
 * 생성하지 않고 [SwitchableTutorEngine]을 통해 현재 활성 모델에 요청을 전달한다.
 *
 * Activity가 회전해도 ViewModelStore에 남으므로 대화/입력 상태가 유지되고,
 * Activity가 종료될 때만 [HomeViewModel.close]가 호출되어 엔진 자원을 해제한다.
 */
class HomeViewModelHost(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val selectionRepository: ModelSelectionRepository =
        PrefsModelSelectionRepository(appContext)

    /** 활성 모델 런타임. GemmaAssistant/QwenAssistant를 여기서 소유하고 전환을 직렬화한다. */
    private val runtimeCoordinator = ModelRuntimeCoordinator(appContext, selectionRepository)

    /** 선택된 모델에 추론 요청을 전달하는 어댑터. */
    private val tutorEngine = SwitchableTutorEngine(runtimeCoordinator)

    val homeViewModel: HomeViewModel = HomeViewModel(
        appContext = appContext,
        scope = viewModelScope,
        tutorEngine = tutorEngine,
        selectionRepository = selectionRepository,
        runtimeCoordinator = runtimeCoordinator
    )

    override fun onCleared() {
        homeViewModel.close()
    }
}