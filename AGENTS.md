# MAR — Mobile Agent Runtime

Android middleware: Kotlin app → C++ JNI (llama.cpp) → on-device LLM.
Rust DAG engine (host-only, not yet wired to Android).

## Build

```bash
./gradlew :demo:app:assembleDebug      # debug APK
./gradlew :demo:app:installDebug       # deploy to device
./gradlew test                          # Kotlin unit tests (JUnit+Robolectric+Mockito)
./gradlew connectedAndroidTest          # instrumented tests (device/emulator)
cargo test                              # Rust DAG tests (host only, no Android)
```

Single test class: `./gradlew test --tests "com.mar.agent.sdk.test.tools.Sprint2UnitTest"`
C++ HAL tests: `cd mar-runtime/native && g++ tests/syscall_test.cpp -o syscall_test -lgtest -lgtest_main -pthread && ./syscall_test`

## Architecture

- **Layer 3 — App**: `demo/app/` — MainActivity, ModelDownloader, YAML sharing, WorkManager scheduling
- **Layer 2 — SDK**: `mar-runtime/agent-sdk/kotlin/` — compiled via `sourceSets { java.srcDir(...) }` in `demo/app/build.gradle.kts`. NOT a standalone library module.
- **Layer 1 — Native**: `mar-runtime/native/` — C++ JNI bridge (`llm_bridge.cpp`, `syscall_bridge.cpp`) + Rust crate (`workflow_dag.rs`)
- **CMake** path in `demo/app/build.gradle.kts` → `../../mar-runtime/native/CMakeLists.txt`
- **Rust** crate has `[lib]` target (default rlib) — no `cdylib`, no JNI exports, not linked into APK
- **llama.cpp** is a git submodule at `mar-runtime/native/llama.cpp/`
- **YAML** (`SnakeYAML` 2.2) drives agent orchestration — see `mar-runtime/agent-sdk/yaml/` for parsers + example `BirthdayAgent.yaml`
- Empty dirs `cli/`, `inference/` are Phase 3 placeholders (not yet implemented)

## Critical Gotchas

- **Do NOT commit** `mar-runtime/native/llama.cpp/` — submodule, gitignored at repo root
- **NDK r26c** required. Set `sdk.dir` + `ndk.dir` in `local.properties` (gitignored)
- **KSP not kapt** — Room uses `ksp("androidx.room:room-compiler")`. Plugin `com.google.devtools.ksp` version `1.9.24-1.0.20` in root `build.gradle.kts`
- **No protobuf code-gen** — `eap.proto` exists but not compiled
- **ChatML format** in `PromptBuilder`: `<|im_start|>`, `<|im_end|>`. `llm_bridge.cpp` uses greedy sampling with early-stop on `]`
- **KV cache / flash-attn** caused 3x ARM slowdown — DO NOT re-enable without testing on target
- **JNI naming**: `Java_com_mar_runtime_core_MarBridge_<method>`
- **Model**: ~350MB `qwen2.5-0.5b-q4_k_m.gguf` from HuggingFace via `ModelDownloader`. Auto-downloads on first run.
- **Android 14 foreground service**: `ScreenCaptureService` — do NOT reorder `startForeground` call (was `ForegroundServiceDidNotStartInTimeException`)
- **SDK class**: `MultiAgentRuntimeManager` (in `core/MarRuntimeManager.kt`) wraps native engine lifecycle + mutex-guarded inference

## Agent Trigger

```bash
adb shell am broadcast -a com.mar.cli.TRIGGER_AGENT --es agent_id "BirthdayGreeter" \
  -n com.mar.demo/com.mar.agent.sdk.cli.TermuxCliReceiver
adb logcat -s FileLogger

# Read crash logs on device
adb shell run-as com.mar.demo cat files/mar_crash_log.txt
```

## CI

`.github/workflows/native-tests.yml`: `cargo test` + `cmake .. && make` on push/PR to `main`.
