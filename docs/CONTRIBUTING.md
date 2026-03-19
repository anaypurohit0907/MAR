# Contributing to MAR 🛠️

Thank you for your interest in scaling edge AI! 

## Building a New Tool 
All developers can hook into the Android system by subclassing `MarTool`. 

1. Create your tool under `mar-runtime/agent-sdk/kotlin/tools/`.
2. Make sure it extends `MarTool(val name: String)`.
3. Standardize your JSON output returns. Do not return raw Kotlin data classes, return serialized strict standard payload blocks so the underlying `workflow_dag` handles it identically across language boundaries!

```kotlin
class SampleTool : MarTool("custom_integration") {
    override fun call(params: Map<String, Any>): String {
       // Access Params
       // Emit JSON explicitly 
       return JSONObject().put("status", "completed").toString()
    }
}
```

## Running Tests
Everything relies on testing boundaries. As features aren't ready, we use **TDD failure tests**. If you're building Phase 3 features (like ScreenOCR), find the corresponding `fail("...");` block in `/tests/` and validate the integration natively. 

Run from the terminal:
```bash
./gradlew connectedAndroidTest # For Emulator Kotlin integration
make test -C native/build      # For C++ layer
cargo test                     # For Rust EAP loop
```
