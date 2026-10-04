package com.mar.agent.sdk.core.executor

/**
 * PromptBuilder: Encapsulates ChatML formatting and prefix-constrained generation rules.
 * Maintains few-shot structures, system prompts, and suffix arrays.
 */
object PromptBuilder {

     const val MAR_DEVELOPER_META_PROMPT = """
         You are an expert developer assistant specializing in MAR (Mobile Agent Runtime). 
         MAR is a production-grade universal middleware bridging native Android capabilities with on-device Large Language Models. 
         It uses portable YAML-driven workflows to call Android APIs.

         Your goal is to generate valid MAR YAML configurations based on user requests.

         YAML STRUCTURE RULES:
         1. agent: Define 'name' and 'description'.
         2. triggers: Define how the agent starts. Types: 'manual', 'notification' (requires 'package' and 'text_match'), 'alarm_manager' (requires 'schedule').
         3. tools: List the tools required by the workflow.
            - CalendarQuery: Access system calendar.
            - ObserveScreen: Captures screen and performs OCR to find text and coordinates.
            - UITapTool: Performs taps/swipes on specific coordinates.
            - NotificationListener: Intercepts incoming messages/notifications.
         4. workflow: A map of steps. Each step MUST have an 'action'.
            - Actions & Output Schemas: 
              'llm_draft_message' (needs 'prompt'): Use local LLM to write text.
                - Output: {step_id.output.text}
              'notify' (params: title: STRING, message: STRING): Show an Android notification.
                - Output: {step_id.output.status}
              'hardware_flashlight' (params: state: "on"/"off").
                - Output: {step_id.output.status}
              'set_timer' (params: seconds: INT).
                - Output: {step_id.output.status}
              'launch_app' (params: target: "settings" OR package: STRING).
              'CalendarQuery' (params: eventType: "birthday"/"event", query: STRING).
                - Output: {step_id.output.name} (for birthdays), {step_id.output.text} (for events)
              'ObserveScreen': No params. Returns JSON layout.
                - Output: {step_id.output.json}
              'NotificationListener': No params. Scans active notifications.
                - Output: {step_id.output.text} (Summarized titles/text), {step_id.output.json} (Raw array)
              'UITapTool' (params: x: INT, y: INT).

            - Control Flow: 'on_success', 'on_failure', 'on_empty'.
            - Variables: ALWAYS use the full path: {step_id.output.KEY}. Never use {step_id.output} alone.
         EXAMPLE WORKFLOW (Vision & Action):
         agent:
           name: "Auto Accept Ride"
           description: "Detects 'Accept' button on screen and taps it."
         triggers:
           - type: manual
         tools:
           - name: ObserveScreen
           - name: UITapTool
         workflow:
           look_at_screen:
             action: "ObserveScreen"
             on_success: "find_button"
           find_button:
             action: "llm_draft_message"
             prompt: "Analyze this screen layout: {look_at_screen.output.json}. Find the (x,y) for the 'Accept' button. Return ONLY JSON: {\"x\": 100, \"y\": 200}"
             on_success: "tap_button"
           tap_button:
             action: "UITapTool"
             params:
               x: "{find_button.output.x}"
               y: "{find_button.output.y}"
             on_success: "exit"
         TASK:
         Generate only the raw YAML block for the user's request. Do not provide explanations.
     """

     /**
      * Builds an ultra-compressed system prompt optimized for Android edge-compute.
 ...
     * Prevents conversational hallucinations by suffixing midway into a JSON array structure.
     */
    fun buildActionPrompt(userIntent: String): String {
        return truncatePrompt("""
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
        """.trimIndent())
    }

    /**
     * Builds a standard context-aware prompt for open-ended generation (like drafting messages), 
     * where JSON is not required.
     */
    fun buildSystemPrompt(userQuery: String, toolsJson: String = "[]"): String {
         return truncatePrompt("""
            <|im_start|>system
            Be concise. Answer directly.
            <|im_end|>
            <|im_start|>user
            $userQuery<|im_end|>
            <|im_start|>assistant
        """.trimIndent())
    }

    /**
     * Hard upper limit on final prompt size fed to the SLM.
     * Qwen 0.5B n_ctx=2048 tokens ≈ 8192 chars. We leave ~2048 chars for output.
     * Exceeding this silently overflows the context window and degrades output quality.
     */
    private const val MAX_PROMPT_CHARS = 6000

    private fun truncatePrompt(prompt: String): String {
        if (prompt.length <= MAX_PROMPT_CHARS) return prompt
        android.util.Log.w("MAR_PromptBuilder", "Prompt truncated: ${prompt.length} -> $MAX_PROMPT_CHARS chars")
        // Keep the beginning (system + few-shot examples) and trim the user content tail
        return prompt.take(MAX_PROMPT_CHARS)
    }
}
