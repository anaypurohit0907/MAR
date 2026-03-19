# Mobile Agent Runtime (MAR) 📱🧠

**The De-Facto Standard for On-Device Agentic AI** (Zero Cloud, Zero Root)

MAR is a production-grade universal middleware bridging native Android capabilities with on-device Large Language Models. It empowers mobile devices with self-contained, offline Agent capabilities.

## 🚀 The Mission
To achieve OSS adoption as the **"Docker for On-Device AI"**, empowering developers to ship small, fast, highly intelligent offline agents utilizing compressed SLMs (Small Language Models) like `Qwen2.5-0.5B`.

### Key Features
*   **100% Offline AI Engines**: Uses native C++ (`llama.cpp`) to run GGUF models directly on device. No API limits, no network latency, complete data privacy.
*   **Zero Root Required**: Fully exploits standard Android APIs (Hardware Cameras, `AlarmClock`, `Intent` Systems) locally via safe Sandbox Hooks.
*   **Cutting-Edge Edge Optimization**:
    *   **Test-Time Compute / Early Stopping**: Drastically speeds up execution by terminating inference the exact millisecond the required JSON bracket ends.
    *   **Vector-First Routing Bypass**: Captures known intents (e.g., "turn on flashlight") bridging direct Action execution in 1ms without utilizing LLM tokens.
    *   **Dynamic Prompt Optimization**: Prefix-constrained generation stripped of conversational bloat ensures lightning-fast evaluation (TPS).
*   **Decoupled Architecture**: Strictly isolates JNI Inference (`hello_mar.cpp`), Routing & Sandbox (`MarAgentWorker`), Prompting (`PromptBuilder`), and OS Intents (`ActionExecutor`).

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
