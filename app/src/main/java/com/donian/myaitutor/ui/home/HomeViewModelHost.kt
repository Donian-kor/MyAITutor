package com.donian.myaitutor.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

/**
 * [HomeViewModel]의 수명 주기를 담당하는 호스트.
 *
 * Activity가 회전해도 ViewModelStore에 남으므로 대화/입력 상태가 유지되고,
 * Activity가 종료될 때만 [HomeViewModel.close]가 호출되어 엔진 자원을 해제한다.
 */
class HomeViewModelHost(application: Application) : AndroidViewModel(application) {

    val homeViewModel: HomeViewModel = HomeViewModel(
        appContext = application.applicationContext,
        scope = viewModelScope
    )

    override fun onCleared() {
        super.onCleared()
        homeViewModel.close()
    }
}