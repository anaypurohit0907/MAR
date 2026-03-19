# MAR Architecture 🏗️

The MAR stack is purposefully divided into three rigid layers.

## Layer 1: Hardware Abstraction Layer (C++/NDK)
**Location:** `mar-runtime/native/src/hal/`

We bypass standard Java-heavy serialization loops where performance matters using NDK. 
*   **Syscall Intercepts**: Standard POSIX operations (open, read, write) are delegated safely into Android's native layer. 
*   **Safety**: Even though we use `C++`, we adhere deeply to Android's app-sandbox constraints. There's no root or Magisk module required.

## Layer 2: EAP Runtime Core (Rust)
**Location:** `mar-runtime/native/src/runtime/`

Handles the **Edge Agent Protocol (EAP)** and Workflow DAG. EAP is a `protobuf` definition standardizing how a Large Language Model formulates thoughts into strict Tool Calls natively.
*   **Why Rust?** The infinite loop in `workflow_dag.rs` manages high-speed DAG transitions. Rust's memory safety explicitly eliminates the segfaults common in prolonged generative workflows navigating unpredictable OS states.
*   **EAP Protocol**: Wraps Tool contexts & Responses so the Kotlin layer doesn't need to parse raw generative strings; it simply receives strictly typed `Observation` and `ToolCall` structures.

## Layer 3: Kotlin Agent SDK
**Location:** `mar-runtime/agent-sdk/kotlin/`

The developer's playground. 
*   **WorkManager Integration**: `MarAgentWorker.kt` elevates runtime priorities into Android Context Foregrounds preventing OS throttling.
*   **Agent DSL**: Employs strictly typed builders allowing complex chaining logic `agent("Name") { ... }`.
*   **Tool Registry**: Built-in mapping resolving `LocalSearchTool` and `UITapTool` to physical capabilities using `Context` permissions securely.
