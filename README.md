# Mobile Agent Runtime (MAR) 📱🧠

**The De-Facto Standard for On-Device Agentic AI** (Zero Cloud, Zero Root)

MAR is a production-grade universal middleware sitting between Android syscalls and Agent DAG (Directed Acyclic Graph) workflows. It empowers 4B+ mobile devices with self-contained, offline Agent capabilities.

## 🚀 The Mission
To achieve viral OSS adoption as the **"Docker for On-Device AI"**, empowering developers to ship small, fast, highly intelligent agents utilizing models like `Qwen3.5-0.8B`. 

### Key Features
*   **Zero Root Required**: Fully exploits standard Android APIs (Accessibility, ScopedStorage, ContentResolvers) locally via C++ HAL.
*   **Zero Cloud Reliance**: Runs 100% locally. No API limits, no network latency.
*   **Survives App Kills**: Doze-mode resilient state management mapping DAG memories directly into SQLite via `Room`.
*   **Declarative Tooling**: Powerful Kotlin DSL & YAML syntax bridging into the Rust core protocol.

## 📦 Quick Start
1.  Clone the repository and open in Android Studio.
2.  Build the Native libraries via CMake (`mar-runtime/native/CMakeLists.txt`).
3.  Deploy the Demo App (`demo/app/`) to any Android 11+ physical device (API 30+).
4.  Run the **1-Tap Install 'Birthday Agent'** to view the live EAP memory execution!

## 🧩 How to Build an Agent
Agents can be defined locally using Kotlin DSL or distributed using YAML configs:

```yaml
agent:
  name: "BirthdayGreeter"
  model: "qwen3.5-0.8b-q4f16"
workflow:
  step_1:
    action: "CalendarQuery"
    params: { query: "today", event_type: "birthday" }
```

You can literally share your agents over intent URIs via `mar-agent://install`!

## 📚 Project Layout
*   `mar-runtime/native`: C++ & Rust core bridging Android APIs and LiteRT hardware acceleration.
*   `mar-runtime/agent-sdk/kotlin`: The exposed Android APIs, background Service bindings, and Tool definitions.
*   `demo/`: A fully self-contained Android APK wrapper testing real world metrics.
*   `docs/`: Full Architectural & Contributing specifications.

Enjoy building the next era of edge computing. 🚀
