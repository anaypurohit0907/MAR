package com.mar.agent.sdk.core.executor

/**
 * PromptBuilder: Encapsulates ChatML formatting and prefix-constrained generation rules.
 * Maintains few-shot structures, system prompts, and suffix arrays.
 */
object PromptBuilder {

    /**
     * Builds an ultra-compressed system prompt optimized for Android edge-compute.
     * Prevents conversational hallucinations by suffixing midway into a JSON array structure.
     */
    fun buildActionPrompt(userIntent: String): String {
        return """
            <|im_start|>system
            Output only JSON array of action objects.
            Available actions: set_timer, hardware_flashlight, open_url, CalendarQuery, ObserveScreen, launch_app.
            <|im_end|>
            <|im_start|>user
            timer 10m laundry<|im_end|>
            <|im_start|>assistant
            [{"action":"set_timer","seconds":600,"message":"laundry"}]<|im_end|>
            <|im_start|>user
            turn on flashlight<|im_end|>
            <|im_start|>assistant
            [{"action":"hardware_flashlight","state":"on"}]<|im_end|>
            <|im_start|>user
            $userIntent<|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    /**
     * Builds a standard context-aware prompt for open-ended generation (like drafting messages), 
     * where JSON is not required.
     */
    fun buildSystemPrompt(userQuery: String, toolsJson: String = "[]"): String {
         return """
            <|im_start|>system
            Be concise. Answer directly.
            <|im_end|>
            <|im_start|>user
            $userQuery<|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }
}
