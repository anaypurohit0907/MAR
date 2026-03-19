# Phase 3 Roadmap: Agents with Senses & Swarms 🌌

We've successfully established a locally hosted, highly secure, zero-cloud execution chain bridging into LiteRT hardware nodes. Now we aim for true multi-modal edge execution allowing tools to coordinate complex logic.

## 🎯 Milestones

### 1. The Screen OCR Projection Layer (Vision)
In phase 3, we allow the DAG engine to observe raw visual layouts, negating the need for explicit UI identifiers relying strictly on standard bounding-box vision parsing mapped locally.

**Expected Flow**:
*   Implement `MediaProjectionManager` to gain display surface capture permissions safely.
*   Capture periodic `Bitmap` artifacts.
*   Route through a localized PyTorch or LiteRT native OCR segmentation component mapping (`x,y,w,h`) structures.
*   Pipe parsed `Observation` data into EAP Protobuf loops.

### 2. Multi-Agent DAG Orchestration
Upgrade the core structure to allow branching graphs representing multiple agents negotiating.

**Expected Flow:**
*   Add logic in `AgentDSL.kt` allowing an agent to yield constraints natively:
    ```kotlin
    val planner = agent("Planner") { ... }
    val worker = agent("Worker") {
      collaboratesWith = listOf(planner)
    }
    ```
*   Rust Core logic needs updating to process multiple simultaneous `task_id` requests resolving into master contextual prompts.

### 3. Audio/Voice Interception Loop
Granting the agent real-time TTS/STT capabilities. Standard offline text-to-speech mapping bindings from `android.speech.tts.TextToSpeech`.
