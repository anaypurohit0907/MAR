# MAR — Mobile Agent Runtime

Android middleware: Kotlin app → C++ JNI (llama.cpp) → on-device LLM.
Rust DAG engine (host-tested, not linked into the APK by default).

## Build

```bash
./gradlew :demo:app:assembleDebug      # debug APK
./gradlew :demo:app:installDebug       # deploy to device
./gradlew test                          # Kotlin unit tests (JUnit+Robolectric+Mockito)
./gradlew connectedAndroidTest          # instrumented tests (device/emulator)
cargo test                              # Rust DAG tests (run in mar-runtime/native)
```

- Single test class: `./gradlew test --tests "com.mar.agent.sdk.test.tools.Sprint2UnitTest"`
- C++ HAL tests (host g++): `cd mar-runtime/native && g++ tests/syscall_test.cpp -o syscall_test -lgtest -lgtest_main -pthread && ./syscall_test`
- **All SDK Kotlin compiles into the app** via `sourceSets` in `demo/app/build.gradle.kts`. Test code MUST go in `mar-runtime/agent-sdk/src/test/java/` or `src/androidTest/java/` (wired there); a file under `agent-sdk/kotlin/` becomes production code.
- CI (`.github/workflows/native-tests.yml`) runs only `cargo test` + host cmake build. Gradle/Android tests never run in CI.

## Architecture

- **Layer 3 — App**: `demo/app/` — MainActivity, ModelDownloader, YAML QR sharing, WorkManager scheduling
- **Layer 2 — SDK**: `mar-runtime/agent-sdk/kotlin/` — compiled via `sourceSets { java.srcDir(...) }`. NOT a standalone library module.
- **Layer 1 — Native**: `mar-runtime/native/` — C++ JNI (`llm_bridge.cpp`, `syscall_bridge.cpp`) + Rust crate (`src/runtime/workflow_dag.rs`)
- **CMake** path in `demo/app/build.gradle.kts` → `../../mar-runtime/native/CMakeLists.txt`; native target is `mar-runtime`, which also compiles `llama.cpp/common/grammar-parser.cpp`
- **Rust** crate is `crate-type = ["staticlib", "lib"]`, linked into the APK only with cmake `-DMAR_BUILD_RUST=ON` (default OFF; needs rustup android targets)
- **llama.cpp** is a git submodule at `mar-runtime/native/llama.cpp/`
- **YAML** (SnakeYAML 2.2) drives orchestration — parser/validator in `kotlin/yaml/`; example agents in `demo/app/src/main/assets/` and `mar-runtime/agent-sdk/yaml/`
- **Intent routing**: `MarAgentWorker` tries `IntentClassifier` (rules/TFLite) + `SlotExtractor` first; only `INTENT_COMPLEX` reaches the LLM. Trace there before assuming LLM behavior.
- Empty dirs `mar-runtime/agent-sdk/kotlin/cli/` and `mar-runtime/native/src/inference/` are placeholders. Real CLI receiver: `com/mar/agent/sdk/cli/TermuxCliReceiver.kt`.

## Critical Gotchas

- **Do NOT commit** `mar-runtime/native/llama.cpp/` — submodule, gitignored at repo root
- **NDK r26c** required (`ndkVersion = "26.2.11394342"`). Set `sdk.dir` + `ndk.dir` in `local.properties` (gitignored)
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
