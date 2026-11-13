package com.donian.myaitutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.donian.myaitutor.ui.FocusAiApp
import com.donian.myaitutor.ui.home.HomeViewModelHost
import com.donian.myaitutor.ui.theme.FocusAiTheme

class MainActivity : ComponentActivity() {
    private val host: HomeViewModelHost by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusAiTheme {
                FocusAiApp(host)
            }
        }
    }

    override fun onDestroy() {
        // 회전 등으로 Activity가 일시 파괴되는 경우는 제외하고,
        // 뒤로가기·모두닫기·시스템 종료 등 실제 종료 경로에서만
        // 모델 메모리를 즉시 해제합니다.
        if (!isChangingConfigurations) {
            host.homeViewModel.close()
        }
        super.onDestroy()
    }
}
