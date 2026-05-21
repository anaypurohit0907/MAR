# MAR Agent - Session Resume Document

## 📍 Current Phase: Phase 3 (Senses & Swarms) - Vision Integration

### 🚦 State of the Project
- **Phase 2 Complete:** The MVP is fully functional. We successfully replaced the mock C++ litert bridge with the genuine `llama.cpp` JNI wrapper for local LLM inference.
- **UI Architecture:** We have moved away from a traditional chatbox. The orchestration of actions is now entirely driven by a **YAML workflow system** (`SnakeYAML`).
- **Data Flow:** The `ModelDownloader` dynamically pulls the required LLM weights and feeds them safely into the native C++ `MarRuntimeManager`.
- **Vision Subsystem (Ongoing):** Implementation of the `ObserveScreen` agent tool using Android's `MediaProjection` API and Google ML Kit.

### 🏆 Recent Wins (Where we left off)
1. **ScreenCaptureService Stability:** Fixed a fatal Android 14 `ForegroundServiceDidNotStartInTimeException`.
   - The system was failing because `resultCode` was `-1` (`Activity.RESULT_OK`), but our logic checked for `resultCode != -1`.
   - The service now properly correctly starts the Foreground service within the strict Android OS time limits, providing the explicit `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` flag.
2. **Robust Crash Logging:** Created `FileLogger.kt` bound via `Thread.setDefaultUncaughtExceptionHandler` in `MainActivity.kt`. It reliably writes logs and fatal exception traces to `mar_crash_log.txt` locally within the app’s scoped storage, bypassing ADB's truncation of abrupt OS kills.

### 🛠️ Next Immediate Steps for Next Session
1. **Integrate Frame Processing (`ObserveScreenTool.kt` / `VisionProcessor.kt`)**: 
   - Right now, `ScreenCaptureService.latestBitmap` safely and silently holds the live screen frame.
   - We need to capture this internal bitmap and pipe it into Google ML Kit (`com.google.mlkit:text-recognition`).
2. **Generate LLM Spatial Geometry**: 
   - Extract the OCR bounding boxes and text.
   - Map them into the JSON coordinate array payload `(center_x, center_y, w, h)` required by the LLM prompt.
3. **YAML End-to-End Validation**: 
   - Execute an actual workflow that calls `"action": "ObserveScreen"`.
   - Ensure the LLM accurately interprets the offline context from the screen.
4. **Advance Phase 3**:
   - Begin DAG Swarms or Audio/TTS implementation once the Vision Tool strictly functions end-to-end.

### 💻 Helpful Commands
- **Compile APK:** ` ./gradlew :demo:app:assembleDebug`
- **Read Custom Crash Logs:** `adb shell run-as com.mar.demo cat files/mar_crash_log.txt`
- **Filter ADB for App Logs:** `adb logcat -s FileLogger`