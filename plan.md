# MAR — Implementation Tasks

## 🔴 Critical Bug Fixes
- [x] **Bug 1**: ScreenCaptureService ANR — pass HandlerThread to `setOnImageAvailableListener`
- [x] **Bug 2**: WorkflowChat silent failure — show error banner when API key missing
- [x] **Bug 3**: `cpuThreads` ignored in `MarRuntimeManager.initialize()` — hardcoded to 4

## 🟠 Architecture — SLM Harness (SLM as Last Resort)
- [x] **M1**: `ContextCompressor.kt` — compress all tool outputs before prompt injection
- [x] **M2**: `IntentClassifier.kt` + `SlotExtractor.kt` — TFLite pre-router + ML Kit slot filling
- [x] **M2**: `AuxModelManager.kt` — background download infrastructure for micro-models
- [x] **M3**: Prompt size hard limit in `PromptBuilder` — truncate before overflow
- [x] **M4**: Minimal output schemas in `ActionExecutor` — defaults filled by code not SLM
- [x] **M5**: Grammar-constrained decoding (`llama_grammar`) in `llm_bridge.cpp`

## 🟠 Features
- [x] Agent permission sandbox — parse YAML permissions, show pre-run dialog
- [x] Template library + QR code sharing — gallery screen + `zxing` QR generation

## 🟡 Developer Experience
- [x] YAML validator + linter (`YamlValidator.kt`)
- [x] Richer notification triggers (cooldowns, time-windows), cooldown in YAML)
