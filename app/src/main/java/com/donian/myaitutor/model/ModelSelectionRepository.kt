package com.donian.myaitutor.model

import android.content.Context
import androidx.core.content.edit

enum class AppModel { GEMMA, QWEN3 }

/**
 * 저장된 모델 선택값과 자유 대화 사용 여부.
 *
 * 두 값은 항상 정합 상태여야 한다. Qwen은 학습 모드 전용이므로
 * `QWEN3 + chatMode = true` 조합은 저장하지 않으며, 읽을 때 정규화한다.
 */
data class ModelSelection(
    val model: AppModel = AppModel.GEMMA,
    val chatMode: Boolean = false
) {
    /** Qwen은 자유 대화를 지원하지 않는다. */
    val isFreeChatAllowed: Boolean get() = model == AppModel.GEMMA

    /** 이 선택값이 런타임에서 유효한지 여부. */
    val isConsistent: Boolean get() = model == AppModel.GEMMA || !chatMode

    /** 어긋난 조합을 정합 상태로 되돌린다. QWEN3이면 chatMode를 끈다. */
    fun normalized(): ModelSelection =
        if (isConsistent) this else copy(chatMode = false)

    companion object {
        /** 기본값. 저장값이 없으면 이 값을 사용한다. */
        val DEFAULT = ModelSelection()
    }
}

/**
 * 모델 선택값 저장 계약.
 * 테스트에서는 SharedPreferences 없이 동작하는 대역을 주입합니다.
 */
interface ModelSelectionRepository {
    fun get(): ModelSelection
    fun getSelectedModel(): AppModel
    fun isFreeChatEnabled(): Boolean
    fun setSelectedModel(model: AppModel)
    fun setChatMode(enabled: Boolean)
}

/**
 * SharedPreferences 기반 기본 구현.
 *
 * Gemma 다운로드 관리자의 SharedPreferences와 완전히 분리되어 있으며,
 * 기존 Gemma 설정값을 초기화하거나 마이그레이션하지 않는다.
 */
class PrefsModelSelectionRepository(context: Context) : ModelSelectionRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 저장된 선택값을 반환한다.
     *
     * 저장된 값이 `QWEN3 + chatMode = true`처럼 어긋나 있으면
     * `chatMode = false`로 정규화하고 즉시 다시 저장한다 (계획서 0.6).
     */
    override fun get(): ModelSelection {
        val model = runCatching {
            AppModel.valueOf(prefs.getString(KEY_SELECTED_MODEL, AppModel.GEMMA.name) ?: AppModel.GEMMA.name)
        }.getOrDefault(AppModel.GEMMA)
        val chatMode = prefs.getBoolean(KEY_CHAT_MODE, false)
        val selection = ModelSelection(model = model, chatMode = chatMode)
        if (selection.isConsistent) return selection
        return selection.normalized().also { save(it) }
    }

    override fun getSelectedModel(): AppModel = get().model

    override fun isFreeChatEnabled(): Boolean = get().chatMode

    override fun setSelectedModel(model: AppModel) {
        // Qwen을 선택하면 자유 대화는 자동으로 꺼진다. 두 값이 어긋난 상태로 저장하지 않는다.
        val current = get()
        val chatMode = if (model == AppModel.QWEN3) false else current.chatMode
        save(current.copy(model = model, chatMode = chatMode))
    }

    override fun setChatMode(enabled: Boolean) {
        val current = get()
        // Qwen 활성 상태에서는 자유 대화를 켤 수 없다.
        if (enabled && !current.isFreeChatAllowed) return
        save(current.copy(chatMode = enabled))
    }

    private fun save(selection: ModelSelection) {
        prefs.edit {
            putString(KEY_SELECTED_MODEL, selection.model.name)
            putBoolean(KEY_CHAT_MODE, selection.chatMode)
        }
    }

    companion object {
        private const val PREFS_NAME = "focus_ai_model_selection"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_CHAT_MODE = "chat_mode"
    }
}