# MAR — Mobile Agent Runtime 📱🧠

## Project Overview
MAR is a production-grade universal middleware bridging native Android capabilities with on-device Large Language Models. It enables portable YAML-driven workflows to call Android APIs (flashlight, timers, SMS, contacts, accessibility, etc.) — all driven by offline LLM inference via `llama.cpp`.

### Key Technologies
- **Kotlin (Android)**: UI (Compose), Persistence (Room via KSP), Background Work (WorkManager), Coroutines.
- **C++ (NDK)**: Native JNI bridge (`llm_bridge.cpp`) using `llama.cpp` for GGUF inference.
- **Rust**: Workflow DAG engine (`workflow_dag.rs`), currently host-only tests.
- **YAML**: Orchestration logic parsed via `SnakeYAML`.
- **Model**: Optimized for SLMs like `Qwen2.5-0.5B` in GGUF format.

## Architecture
- **Layer 1: Edge Inference Engine (C++/NDK)**: Located in `mar-runtime/native/`. Uses `llama.cpp` for tensor math. Employs structural early stopping (breaks on `]`) for JSON output speed.
- **Layer 2: Android Execution Sandbox (Kotlin SDK)**: Located in `mar-runtime/agent-sdk/kotlin/`. Bridges JNI responses to Android OS features.
    - `ActionExecutor.kt`: Translates JSON instructions into `Intent`s, `CameraManager` calls, etc.
    - `PromptBuilder.kt`: Constructs ChatML-formatted prompts (`<|im_start|>user\n...<|im_end|>\n<|im_start|>assistant\n`).
    - `MarAgentWorker.kt`: Handles persistent execution via WorkManager.
- **Layer 3: Application Integration (Demo App)**: Located in `demo/app/`. UI for model downloading, execution telemetry (Tokens/sec, RAM), and agent management.

## Building and Running
- **Debug APK**: `./gradlew :demo:app:assembleDebug`
- **Deploy to Device**: `./gradlew :demo:app:installDebug`
- **Kotlin Unit Tests**: `./gradlew test`
- **Android Instrumented Tests**: `./gradlew connectedAndroidTest`
- **Rust DAG Tests (Host only)**: `cargo test` (in `mar-runtime/native/`)
- **C++ HAL Tests**: 
  ```bash
  cd mar-runtime/native && g++ tests/syscall_test.cpp -o syscall_test -lgtest -lgtest_main -pthread && ./syscall_test
  ```

## Development Conventions

### Prompting & Inference
- **Format**: Always use ChatML tags: `<|im_start|>`, `<|im_end|>`.
- **JSON Output**: Models are prompted to return JSON. The native engine uses greedy sampling and stops immediately when it detects the closing `]` of the instruction block.
- **Vector-First Routing**: Check for literal intent matches (e.g., "flashlight") in `MarAgentWorker` to bypass LLM inference (latency < 5ms).

### Android Specifics
- **NDK**: Requires NDK r26c.
- **KSP**: Use `ksp` instead of `kapt` for Room (see root `build.gradle.kts`).
- **Foreground Services**: Adhere to Android 14+ requirements for `ScreenCaptureService`.
- **Permissions**: Many actions (SMS, Contacts, Calendar) require runtime permissions. `ActionExecutor` handles `SecurityException` but assumes permissions are granted via UI.

### Native & Submodules
- **llama.cpp**: Integrated as a git submodule in `mar-runtime/native/llama.cpp/`. **Do NOT commit this directory.**
- **JNI Naming**: `Java_com_mar_runtime_core_MarBridge_<method>`.

## Key Files
- `mar-runtime/agent-sdk/kotlin/core/MarRuntimeManager.kt`: Singleton managing engine lifecycle and mutex-guarded inference.
- `mar-runtime/agent-sdk/kotlin/core/executor/ActionExecutor.kt`: The "hands" of the agent, calling Android APIs.
- `mar-runtime/native/src/hal/llm_bridge.cpp`: The native entry point for inference.
- `mar-runtime/agent-sdk/kotlin/yaml/parser/MarYamlParser.kt`: Maps YAML workflows to Kotlin data models.
- `demo/app/src/main/assets/*.yaml`: Pre-defined agent workflows (e.g., `BirthdayAgent.yaml`).
