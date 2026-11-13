package com.donian.myaitutor.ai

/**
 * Qwen3-4B-Instruct-2507 전용 Chat Template.
 *
 * LiteRT-LM 0.18.0부터 message.content가 문자열이 아니라 typed parts 목록으로 전달될 수 있다.
 * 기본 템플릿은 문자열만 읽어 빈 턴이 되므로, 여기서 typed parts의 text를 이어 붙여
 * Qwen ChatML 형식으로 렌더링한다.
 */
object Qwen3ChatTemplate {

    /** LiteRT-LM ConversationConfig.chatTemplate에 전달할 Jinja 템플릿. */
    const val TEMPLATE: String = """
{%- macro render_content(content) -%}
{%- if content is string -%}
{{ content }}
{%- elif content is iterable -%}
{%- for part in content -%}
{%- if part is mapping -%}
{%- if part.text is defined -%}{{ part.text }}{%- elif part['text'] is defined -%}{{ part['text'] }}{%- endif -%}
{%- else -%}{{ part }}{%- endif -%}
{%- endfor -%}
{%- else -%}{{ content }}{%- endif -%}
{%- endmacro -%}
{%- for message in messages -%}
{%- if message['role'] == 'system' -%}
<|im_start|>system
{{ render_content(message['content']) }}<|im_end|>
{%- elif message['role'] == 'user' -%}
<|im_start|>user
{{ render_content(message['content']) }}<|im_end|>
{%- elif message['role'] == 'assistant' -%}
<|im_start|>assistant
{{ render_content(message['content']) }}<|im_end|>
{%- endif -%}
{%- endfor -%}
{%- if add_generation_prompt -%}
<|im_start|>assistant
{%- endif -%}
"""

    /** typed parts 또는 문자열 content에서 텍스트를 추출합니다. */
    fun renderContent(content: Any?): String = when (content) {
        null -> ""
        is String -> content
        is CharSequence -> content.toString()
        is Iterable<*> -> content.joinToString("") { part ->
            when (part) {
                null -> ""
                is String -> part
                is Map<*, *> -> (part["text"] ?: part["content"] ?: "").toString()
                else -> {
                    // reflection-free best effort: try text property via toString? fallback empty
                    part.toString().let { if (it.startsWith("Content.Text")) "" else it }
                }
            }
        }
        else -> content.toString()
    }

    /** 간단한 Kotlin 렌더러. 단위 테스트와 디버깅용입니다. */
    fun render(messages: List<Map<String, Any?>>, addGenerationPrompt: Boolean = true): String {
        val sb = StringBuilder()
        for (m in messages) {
            val role = m["role"]?.toString() ?: "user"
            val content = renderContent(m["content"])
            sb.append("<|im_start|>").append(role).append("\n")
            sb.append(content)
            sb.append("<|im_end|>\n")
        }
        if (addGenerationPrompt) {
            sb.append("<|im_start|>assistant\n")
        }
        return sb.toString()
    }
}