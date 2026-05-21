# MAR — Comprehensive Fix Plan

## Priority: P0 (Crashes & Blockers) → P1 (Code Quality & Security) → P2 (Architectural)

---

## P0: Crashes & Demo-Stoppers

### 1. ScreenCaptureService ANR / UI Thread Freeze
**File**: `mar-runtime/agent-sdk/kotlin/vision/ScreenCaptureService.kt`
**Problem**: The `ImageReader` in `startProjection` registers its listener with a `null` Handler: `imageReader?.setOnImageAvailableListener({ ... }, null)`. This causes pixel copying and Bitmap creation to run synchronously on the Main Thread for every single screen frame, which will cause severe jank and inevitable ANR (Application Not Responding) crashes.
**Fix**: Create a dedicated background `HandlerThread` and `Handler` in `onCreate`, and pass it to `setOnImageAvailableListener` to offload image processing.

### 2. Missing READ_SMS Permission Declaration
**Files**: `demo/app/src/main/AndroidManifest.xml`, `ActionExecutor.kt`
**Problem**: The `read_sms` action queries `content://sms/inbox`, which requires the `READ_SMS` permission. However, this permission is completely missing from `AndroidManifest.xml`, so it can never be requested or granted, causing the action to permanently fail with a `SecurityException`.
**Fix**: Add `<uses-permission android:name="android.permission.READ_SMS" />` to the manifest and update the `checkPermissions()` method in `MainActivity` to request it dynamically.

### 3. API Key Gate Contradicts Offline Pitch
**File**: `demo/app/src/main/java/com/mar/demo/ui/fragments/WorkflowChatFragment.kt`
**Problem**: Generating YAML workflows via chat requires a cloud API key (Gemini API), completely breaking the "100% Offline" marketing pitch for the demo.
**Fix**: Provide a "Demo Mode" fallback that loads pre-built, on-device YAML templates without requiring a cloud connection, or use the local `llama.cpp` model to generate the YAML instead.

---

## P1: Code Quality & Security

### 4. Synchronous Network I/O on Ambiguous Context
**File**: `mar-runtime/agent-sdk/kotlin/core/executor/ActionExecutor.kt`
**Problem**: The `web_fetch` action uses `java.net.URL(url).openConnection()` to perform synchronous HTTP GET requests. If `ActionExecutor` is ever instantiated or called from the UI thread (e.g., during testing or synchronous execution), it will throw a `NetworkOnMainThreadException` and crash the app.
**Fix**: Wrap the network request strictly within `withContext(Dispatchers.IO) { ... }` in the `ActionExecutor`.

### 5. Rust DAG Not Linked into APK (Dead Code)
**Files**: `mar-runtime/native/Cargo.toml`, `CMakeLists.txt`, `workflow_dag.rs`
**Problem**: The Rust workflow DAG compiles as an `rlib` instead of a `staticlib` or `cdylib` and is never linked by CMake into the final `mar-runtime.so` JNI library. As a result, the Kotlin app never invokes Rust, bypassing it entirely.
**Fix**:
1. Change `Cargo.toml` to include `crate-type = ["staticlib"]`.
2. Add `extern "C"` FFI export functions in `workflow_dag.rs`.
3. Add an `ExternalProject_Add` or `add_custom_target` to `CMakeLists.txt` to compile the Rust crate during the NDK build and link it against the C++ wrapper.

### 6. Silent Exception Swallowing hides UI bugs
**Files**: `demo/app/src/main/java/com/mar/demo/ui/UiUtils.kt` (line 120), `YamlEditorFragment.kt` (line 245)
**Problem**: Empty catch blocks (`catch (_: Exception) {}`) silently discard exceptions. If the YAML editor parsing or UI utility fails, the error vanishes, complicating debugging.
**Fix**: Replace empty blocks with explicit logging: `Log.w(TAG, "Error: ${e.message}", e)`.

---

## P2: Architectural Improvements

### 7. Rust DAG Full Integration
**Problem**: The Rust engine (`workflow_dag.rs`) and Kotlin `WorkflowRunner.kt` duplicate execution logic.
**Fix**: Once the `staticlib` is linked (Issue #5), route `WorkflowRunner.kt` step execution through the C++ JNI bridge directly into the Rust DAG state machine.
