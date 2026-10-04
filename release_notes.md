# MAR (Mobile Agent Runtime) - v0.1.0 Official Preview 🚀

This is the initial signed technical preview of **MAR**, a production-grade universal middleware bridging native Android capabilities with on-device Large Language Models.

### ✨ Key Features in this Build
- **On-Device Inference**: Integration with `llama.cpp` for GGUF model execution (optimized for SLMs like Qwen2.5-0.5B).
- **YAML Workflow Engine**: Portable agent definitions that map user intent to Android system actions.
- **Native Android Bridge**: Execution sandbox for calling system APIs (Flashlight, Notifications, UI Observation).
- **Inference Telemetry**: Real-time tracking of Tokens/sec and RAM usage within the Demo app.

### 🛠 How to Install & Verify
1. Download **app-release.apk** below.
2. (Optional) Verify the checksum against **MAR-v0.1.0-SHA256.txt**.
   - Linux/macOS: `sha256sum app-release.apk`
   - Windows (PS): `Get-FileHash app-release.apk -Algorithm SHA256`
3. Install on your Android device (Targeting Android 14+ / API 34).

---

### ⚠️ Disclaimers & Safety Information

**1. Experimental Software**
This is a **pre-alpha release**. It is intended for evaluation purposes only and may contain bugs or performance bottlenecks.

**2. Resource Intensive**
On-device LLM inference is demanding. Running models may cause your device to heat up and will significantly drain the battery.

**3. Data & Privacy**
All inference happens **offline** on your device. Review the requested permissions (Accessibility, Notifications) in the app settings.

**4. No Warranty**
This software is provided "as-is" without warranty of any kind.
