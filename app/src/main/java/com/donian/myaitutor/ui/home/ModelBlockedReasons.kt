package com.donian.myaitutor.ui.home

import com.donian.myaitutor.R
import com.donian.myaitutor.model.AppModel

/**
 * 현재 선택된 모델이 자유 대화를 지원하지 않을 때의 안내 문구.
 * Qwen 활성 시에만 null이 아니며, 자동으로 Gemma를 로드하지 않는다(계획서 0.6).
 */
internal fun freeChatBlockedReasonFor(model: AppModel, stringProvider: StringProvider): String? =
    if (model == AppModel.QWEN3) {
        stringProvider.get(R.string.model_free_chat_blocked_by_qwen)
    } else {
        null
    }

/**
 * 자유 대화 사용 중에는 Qwen 선택을 보류한다는 안내 문구.
 * 사용자가 자유 대화를 끄면 선택할 수 있다.
 */
internal fun modelSelectionBlockedReasonFor(
    model: AppModel,
    chatMode: Boolean,
    stringProvider: StringProvider,
): String? =
    if (model == AppModel.GEMMA && chatMode) {
        stringProvider.get(R.string.model_selection_blocked_by_free_chat)
    } else {
        null
    }
