package com.donian.myaitutor.ui.home

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * 종료 해제 전용 daemon 스레드를 가진 디스패처를 만듭니다.
 * 호출 스레드(UI 스레드)를 점유하지 않고 JVM 종료도 막지 않습니다.
 */
internal fun createShutdownDispatcher(): CoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "focus-ai-engine-shutdown").apply { isDaemon = true }
    }.asCoroutineDispatcher()
