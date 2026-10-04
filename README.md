# Mobile Agent Runtime (MAR) 📱🧠

**A YAML-driven agent runtime for Android** (Zero Cloud, Zero Root)

MAR is a production-grade universal middleware bridging native Android capabilities with on-device Large Language Models. It empowers mobile devices with self-contained, offline Agent capabilities.

## 🚀 The Mission
Portable YAML workflows that call Android APIs (flashlight, timers, SMS, contacts, accessibility, web fetch) — all driven by on-device LLM inference via llama.cpp.

### Key Features
*   **100% Offline AI Engines**: Uses native C++ (`llama.cpp`) to run GGUF models directly on device. No API limits, no network latency, complete data privacy.
*   **Zero Root Required**: Fully exploits standard Android APIs (Hardware Cameras, `AlarmClock`, `Intent` Systems) locally via safe Sandbox Hooks.
*   **Cutting-Edge Edge Optimization**:
    *   **Test-Time Compute / Early Stopping**: Drastically speeds up execution by terminating inference the exact millisecond the required JSON bracket ends.
    *   **Vector-First Routing Bypass**: Captures known intents (e.g., "turn on flashlight") bridging direct Action execution in 1ms without utilizing LLM tokens.
    *   **Dynamic Prompt Optimization**: Prefix-constrained generation stripped of conversational bloat ensures lightning-fast evaluation (TPS).
*   **Decoupled Architecture**: Strictly isolates JNI Inference (`llm_bridge.cpp`), Routing & Sandbox (`MarAgentWorker`), Prompting (`PromptBuilder`), and OS Intents (`ActionExecutor`).

## 🛣️ Roadmap: The Fast Path

MAR v0.1.0 runs fully offline on a `Qwen2.5-0.5B` GGUF model (~10s per LLM inference), with the rules/TFLite tier handling known commands in under 5ms. The next phase is a MAR-specialized inference stack that brings the rest down to milliseconds-to-low-seconds — still 100% on-device, zero-cloud.

### Two specialized models, not one

*   **Decision model (non-autoregressive)**: A tiny System One–style model that returns typed choices with calibrated probabilities in a single forward pass. It never generates text — it selects tools, gates arguments, and matches triggers. Target: ~10-30ms per decision, shipped as TFLite/ONNX.
*   **Tiny generator (autoregressive)**: A fine-tuned / distilled 135M-0.5B model trained on MAR's exact ChatML + action JSON contract. Used only when language actually needs to be written (message drafting, open-ended slots, hard fallbacks). Quantized to GGUF and constrained by a GBNF JSON grammar so output is always valid.

### Execution tiers (target)

| Tier | Handles | Target latency |
| :--- | :--- | ---: |
| Rules | Flashlight, timers, known intents | < 1 ms |
| Decision model | Tool selection, argument gating, triggers | ~10-30 ms |
| Tiny generator + grammar | Drafting, open slots, complex actions | ~1-3 s |
| Current 0.5B | Hard / creative fallback | ~10 s |

Most commands should never reach autoregressive decoding at all.

### Build order

1.  **Grammar-constrained decoding (GBNF)** — no training required; guarantees valid JSON action output.
2.  **Prompt shrink** — fine-tuning removes few-shot examples from the prompt, cutting prefill cost.
3.  **Decision model** — distill teacher labels into a small typed-decision head.
4.  **Tiny generator** — fine-tune/distill on synthetic MAR trajectories, quantize, benchmark on device.
5.  **Eval harness** — held-out utterances → expected actions, so regressions are measurable.

All models remain fully local and offline; nothing in this plan introduces a cloud dependency.

## 📦 Quick Start
1.  Clone the repository and open in Android Studio.
2.  The native inference engine (`llama.cpp`) is securely submoduled. Build native libraries natively via Gradle edge CMake targets.
3.  Deploy the Demo App (`demo/app/`) to any Android 11+ physical device (API 30+).
4.  In the app, download the `Qwen2.5-0.5B` GGUF model directly via the UI downloader.
5.  Type commands like *"turn on flashlight"* or *"set a 5 minute timer for cooking"* into the console and watch native OS intents trigger instantly offline!

## 📚 Project Layout
*   `mar-runtime/native`: C++ and `llama.cpp` integration. The absolute core engine for AI tensor mathematics, context memory, and JNI.
*   `mar-runtime/agent-sdk/kotlin`: The Android execution sandbox, holding `ActionExecutor`, `MarAgentWorker`, and scheduling structures bridging JNI responses to hardware features.
*   `demo/`: A fully self-contained Android APK wrapper providing real-time telemetry (Tokens/sec, RAM, etc) and execution logging UI.
*   `docs/`: Full Architectural & Contributing specifications.

Enjoy building the next era of edge computing. 🚀
