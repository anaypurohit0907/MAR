# MAR Runbook: How to Build, Run, and Test 🚀

This guide covers everything you need to know to compile the Mobile Agent Runtime (MAR), run the test suites locally on your laptop, and deploy the demo natively to your Android phone.

---

## 🛠️ Phase 1: Prerequisites (Laptop Setup)

Before building, ensure your local development environment has the necessary toolchains for our cross-language architecture.

### 1. Install Android dependencies (Linux)

**Installing Android Studio (from .tar.gz)**
If you downloaded the official `android-studio-*.tar.gz` from the Google Developer site, follow these steps to extract and run it:
```bash
# Extract the archive into your /opt directory (recommended for external software)
sudo tar -xzf ~/Downloads/android-studio-*.tar.gz -C /opt/

# Start Android Studio setup wizard
/opt/android-studio/bin/studio.sh
```
*Note: During the initial setup wizard, Android Studio will offer to automatically download the Android SDK, Build Tools, and Emulator images. Allow it to do so.*

**ADB (Android Debug Bridge)**
If Android Studio didn't link `adb` to your global path, or if you prefer the standard system package:
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install android-tools-adb -y

# Fedora
sudo dnf install android-tools -y
```

**CMake & Standard Build Tools**
```bash
# Ubuntu/Debian
sudo apt-get install build-essential cmake -y

# Fedora
sudo dnf install gcc gcc-c++ make cmake -y
```

**Android NDK (Command Line Install)**
If you are building strictly from the command line without Android Studio:
```bash
wget https://dl.google.com/android/repository/android-ndk-r26c-linux.zip
unzip android-ndk-r26c-linux.zip -d ~/android-sdk/ndk/
export ANDROID_NDK_HOME=~/android-sdk/ndk/android-ndk-r26c
```
*(Alternatively, simply open Android Studio -> Tools -> SDK Manager -> SDK Tools -> Check `NDK (Side by side)` and `CMake`)*

### 2. Install Rust
Install Rust using `rustup` to build the core DAG engine:
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```
Add the Android cross-compilation targets:
```bash
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

---

## 💻 Phase 2: Testing on Your Laptop (Core Logic)

You can verify the core runtime boundaries (Memory loop constraints, EAP configurations) purely on your laptop without a phone.

### 1. Test the Rust Runtime (DAG Logic)
Navigate to the native directory and run standard Cargo tests:
```bash
cd mar-runtime/native
cargo test
```
*Expected Output:* You should see `3 passed; 0 failed` indicating the Context Auto-Summarization and Infinite Loop Prevention systems are active.

### 2. Test the Native C++ Hal (GTest)
To test the C++ Syscall bridges bridging POSIX limits:
```bash
cd mar-runtime/native
# Assuming you have gtest installed locally on Linux
g++ tests/syscall_test.cpp -o syscall_test -lgtest -lgtest_main -pthread
./syscall_test
```

---

## 📱 Phase 3: Running on Your Phone (The Demo App)

To see the Agent Runtime in action, we deploy the APK locally to your physical device.

### 1. Phone Preparation
*   Go to **Settings > About Phone** -> Tap **Build Number** 7 times.
*   Go to **Settings > Developer Options** -> Enable **USB Debugging**.
*   Plug your phone into your laptop via USB. Verify connection:
    ```bash
    adb devices
    ```

### 2. Build and Deploy the APK
You can open the `/home/anay/Desktop/MAR` project in Android Studio and hit the green **▶ Run** button. Alternatively, use the Gradle CLI:

```bash
cd /home/anay/Desktop/MAR
# Make the gradlew script executable (if you have standard gradle wrappers initialized)
chmod +x gradlew
./gradlew :demo:app:installDebug
```

### 3. Using the Demo App
1. Open the **MAR Demo** app on your phone.
2. Grant requested permissions (Calendar, Contacts, Battery Optimization bypass).
3. Tap **"1-Tap Install & Run 'Birthday Agent'"**.
4. Watch the **Live System Metrics** text view dynamically report the mock tokens/sec output and RAM bounds.

---

## 👨‍💻 Phase 4: Power-User Testing via Android Termux

We built an Intent Receiver that allows system-wide CLI invocations. You can test this via ADB from your laptop OR via Termux directly on your phone.

### Trigger from Laptop (via ADB)
While the phone is plugged in, trigger the agent execution background worker manually:
```bash
adb shell am broadcast -a com.mar.cli.TRIGGER_AGENT --es agent_id "BirthdayGreeter" -n com.mar.demo/com.mar.agent.sdk.cli.TermuxCliReceiver
```

### Trigger from Phone (via Termux)
1. Download **Termux** from F-Droid.
2. Run the exact same broadcast intent format:
```bash
am broadcast -a com.mar.cli.TRIGGER_AGENT --es agent_id "BirthdayGreeter"
```
*Expected Behavior:* Even if MAR is closed, an Android Notification will pop up in your notification shade showing: `Agent Running: BirthdayGreeter` with options to Pause/Resume the WorkManager job!

---

## 🧪 Phase 5: Android Instrumented Tests (On-Device)

Finally, to test the Room Database memory persistence, vector storage hooks, and Kotlin SDK integrations against a real Android OS Environment:

```bash
cd /home/anay/Desktop/MAR
./gradlew connectedAndroidTest
```
*Note: This strictly requires an emulator running or your phone plugged in, as it boots a temporary test APK to validate physical SQLite writes and Sandbox limits.*
