package com.mar.runtime.core

/**
 * Kotlin JNI Bridge connecting the Layer 3 Agent SDK to Layer 1/2 NDK Runtime.
 */
object MarBridge {
    init {
        // Load the compiled C++/Rust HAL and Runtime
        System.loadLibrary("mar-runtime")
    }

    /**
     * Initializes the native MAR environment, mapping NPU/GPU memory via LiteRT.
     * @param maxRamMb Maximum RAM in MB the quantized model is allowed to use.
     * @param threads Number of CPU threads for fallback execution.
     * @return true if initialization is successful.
     */
    external fun initialize(maxRamMb: Int, threads: Int): Boolean

    /**
     * Test primitive for the native inference loop.
     * @param prompt The incoming serialized EAP Observation or prompt.
     * @return The serialized next ToolCall or EAP Message.
     */
    external fun runInferenceTest(prompt: String): String
}
