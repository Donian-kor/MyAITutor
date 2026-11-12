package com.donian.myaitutor.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.donian.myaitutor.model.ModelDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Owns one LiteRT-LM Engine and one multi-turn Conversation.
 * All native runtime operations are serialized on one dedicated thread.
 */
class GemmaAssistant(context: Context) {
    private val appContext = context.applicationContext
    private val runtimeDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "focus-ai-litert-runtime").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var selectedBackendLabel: String = "ë¯¸ì‹¤??

    suspend fun analyzeImage(
        imageFile: File,
        subject: String,
        onTextChanged: suspend (String) -> Unit
    ) = withContext(runtimeDispatcher) {
        require(imageFile.exists() && imageFile.length() > 0L) { "ì´¬ì˜ ?´ë?ì§€ê°€ ?†ìŠµ?ˆë‹¤." }
        ensureInitializedOnRuntimeThread()

        val prompt = """
            [???™ìŠµ ë¬¸ì œ]
            ? íƒ ê³¼ëª©: $subject

            ì¹´ë©”???´ë?ì§€???ˆëŠ” ë¬¸ì œë¥?ì§ì ‘ ?½ê³  ë¶„ì„??ì£¼ì„¸??
            OCR ?ìŠ¤?¸ë§Œ ê°€?•í•˜ì§€ ë§ê³  ?´ë?ì§€??ë¬¸ìž¥, ?˜ì‹, ?? ê·¸ëž˜?? ?„í˜•???¨ê»˜ ?•ì¸?˜ì„¸??

            ?¤ì œ ? ìƒ?˜ì²˜???ì—°?¤ëŸ¬???œêµ­??ì¡´ëŒ“ë§ë¡œ ?¤ëª…?˜ì„¸??
            ?¤ìŒ ?œì„œë¡??µë??˜ì„¸??
            1. ë¬¸ì œ?ì„œ ë¬»ëŠ” ?´ìš© ?•ë¦¬
            2. ?„ìš”??ê¸°ì´ˆ ê°œë… ?¤ëª…
            3. ?µì‹¬ ?ë¦¬?€ ê³µì‹ ?¤ëª…
            4. ë¬¸ì œ??ê°œë… ?ìš©
            5. ?¨ê³„ë³??ì„¸ ?€??            6. ìµœì¢… ?•ë‹µ
            7. ê²€???ëŠ” ê·¼ê±° ?•ì¸
            8. ë§ˆì?ë§‰ì— ?´í•´ë¥??•ëŠ” ì§§ì? ?•ì¸ ì§ˆë¬¸

            ?´ë?ì§€ê°€ ?ë¦¬ê±°ë‚˜ ë¬¸ì œ ?¼ë?ê°€ ?˜ë ¸ê±°ë‚˜ ?˜ì‹Â·?„í˜•???ë…?????†ë‹¤ë©?ì¶”ì¸¡?˜ì? ë§ê³ ,
            ?´ë–¤ ë¶€ë¶„ì„ ?¤ì‹œ ì´¬ì˜?´ì•¼ ?˜ëŠ”ì§€ êµ¬ì²´?ìœ¼ë¡??Œë ¤ì£¼ì„¸??
            ?¬ëŸ¬ ë¬¸ì œê°€ ë³´ì´ë©?ê°€??ì¤‘ì•™???ˆëŠ” ë¬¸ì œ ?˜ë‚˜ë¥??°ì„  ë¶„ì„?˜ê³ , ë¬¸ì œê°€ ?¬ëŸ¬ ê°œë¼ë©?            ?¬ìš©?ê? ? íƒ?˜ë„ë¡??ˆë‚´?˜ì„¸??
        """.trimIndent()

        streamResponse(
            conversation = requireNotNull(conversation),
            contents = Contents.of(
                Content.ImageFile(imageFile.absolutePath),
                Content.Text(prompt)
            ),
            onTextChanged = onTextChanged
        )
    }

    suspend fun askFollowUp(
        question: String,
        onTextChanged: suspend (String) -> Unit
    ) = withContext(runtimeDispatcher) {
        require(question.isNotBlank()) { "ì§ˆë¬¸???…ë ¥??ì£¼ì„¸??" }
        ensureInitializedOnRuntimeThread()
        streamResponse(
            conversation = requireNotNull(conversation),
            contents = Contents.of(Content.Text(question)),
            onTextChanged = onTextChanged
        )
    }

    suspend fun backendLabel(): String = withContext(runtimeDispatcher) { selectedBackendLabel }

    private suspend fun ensureInitializedOnRuntimeThread() {
        if (engine != null && conversation != null) return

        val modelFile = ModelDownloadManager(appContext).modelFile
        require(modelFile.isFile && modelFile.length() >= 3_500_000_000L) {
            "Gemma 4 E4B ëª¨ë¸???¤ì¹˜?˜ì? ?Šì•˜?µë‹ˆ?? ?¤ì •?ì„œ ëª¨ë¸???¤ìš´ë¡œë“œ??ì£¼ì„¸??"
        }

        val systemInstruction = """
            ?¹ì‹ ?€ MyAITutor????ê³¼ëª© ?„ë¬¸ ?™ìŠµ ? ìƒ?˜ìž…?ˆë‹¤.
            ëª©í‘œ???™ìƒ???•ë‹µ???¸ìš°??ê²ƒì´ ?„ë‹ˆ??ê°œë…???´í•´?˜ë„ë¡??•ëŠ” ê²ƒìž…?ˆë‹¤.
            ?¸ì œ??ì¹œì ˆ?˜ê³  ì°¨ë¶„???œêµ­??ì¡´ëŒ“ë§ì„ ?¬ìš©?˜ì„¸??
            ë¬¸ì œë¥?ë°›ìœ¼ë©?ê°œë…ê³??ë¦¬ë¥?ë¨¼ì? ?¤ëª…?˜ê³ , ê·¸ë‹¤??ë¬¸ì œ ?ìš©ê³??¨ê³„ë³??€?´ë? ?œì‹œ?˜ì„¸??
            ê·¼ê±°ê°€ ë¶€ì¡±í•˜ê±°ë‚˜ ?´ë?ì§€ê°€ ë¶ˆëª…?•í•˜ë©??•ë‹µ??ê¾¸ë©°?´ì? ë§ê³  ?•ì¸???”ì²­?˜ì„¸??
            ?™ìƒ???„ì† ì§ˆë¬¸???˜ë©´ ?„ìž¬ ?€?”ì˜ ë¬¸ì œ?€ ?žì„  ?¤ëª…??ê¸°ì–µ???´ì–´???µí•˜?¸ìš”.
            ?˜í•™ ?œí˜„?€ ?½ê¸° ?¬ìš´ ?˜ì‹ ?œê¸°ë¡??‘ì„±?˜ê³ , ?„ìš”??ê²½ìš° LaTeX ?œê¸°ë¥??¬ìš©?˜ì„¸??
        """.trimIndent()

        val configs = listOf(
            "GPU" to EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU(),
                visionBackend = Backend.GPU(),
                cacheDir = appContext.cacheDir.absolutePath
            ),
            "CPU" to EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
                cacheDir = appContext.cacheDir.absolutePath
            )
        )

        var lastError: Throwable? = null
        for ((backendName, config) in configs) {
            val candidate = Engine(config)
            var initialized = false
            try {
                candidate.initialize()
                initialized = true
                val candidateConversation = candidate.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemInstruction),
                        samplerConfig = SamplerConfig(
                            topK = 20,
                            topP = 0.9,
                            temperature = 0.35
                        ),
                        maxOutputToken = 2048
                    )
                )
                engine = candidate
                conversation = candidateConversation
                selectedBackendLabel = backendName
                return
            } catch (error: Exception) {
                lastError = error
                // Do not call close() on an Engine whose initialize() failed.
                // If initialization succeeded but conversation creation failed, release it.
                if (initialized) runCatching { candidate.close() }
            }
        }

        throw IllegalStateException(
            "Gemma 4 E4Bë¥?GPU?€ CPU?ì„œ ëª¨ë‘ ì´ˆê¸°?”í•˜ì§€ ëª»í–ˆ?µë‹ˆ?? ê¸°ê¸° ë©”ëª¨ë¦¬ì? ëª¨ë¸ ?Œì¼???•ì¸??ì£¼ì„¸??",
            lastError
        )
    }

    private suspend fun streamResponse(
        conversation: Conversation,
        contents: Contents,
        onTextChanged: suspend (String) -> Unit
    ) {
        val accumulated = StringBuilder()
        conversation.sendMessageAsync(contents).collect { message ->
            accumulated.append(message.toString())
            withContext(Dispatchers.Main.immediate) {
                onTextChanged(accumulated.toString())
            }
        }
    }

    suspend fun release() = withContext(runtimeDispatcher) {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        selectedBackendLabel = "ë¯¸ì‹¤??
    }
}
