# 20-20-20 Eye Care Android Application

A highly reliable, visually soothing visual wellness mobile application built with **Kotlin, Jetpack Compose, and Material Design 3**. It is specifically structured to run high-precision eye strain prevention timers implementing the classic **20-20-20 Rule**.

---

## 👁️ Core Logic (The 20-20-20 Rule)
*   **The Cycle:** The app runs a background timer counting up to **20 minutes**.
*   **The Break Alert:** When 20 minutes is reached, a low-vibration, high-priority heads-up status bar notification alerts you: *"Time to rest your eyes (20 seconds)"*.
*   **The Rest Timer:** A separate **20-second resting timer** begins immediately, pausing the main loop. Once complete, the system resets the active worker and repeats.
*   **Screen-Off Intelligence:**
    *   If you lock your phone and unlock it within **20 seconds**, the work timer **continues** from where you left off.
    *   If you leave your phone locked for **more than 20 seconds**, the screen-off logic detects that your eyes have rested, and the work timer **resets fresh** to 20 minutes.

---

## ⚙️ Key Technical Features
1.  **Background Foreground Service:** Employs a sticky Android `ForegroundService` with a low-priority active notification to satisfy modern system background executions.
2.  **Special Use Class declaration:** Fully declared under Android 14+ special background execution restrictions using high-priority special-use tags and metadata properties.
3.  **High-Precision Wall Clock Timing:** Avoids standard handler drifts by measuring time using `SystemClock.elapsedRealtime()`, tracking elapsed periods precisely even during Doze/deep-sleep modes.
4.  **PowerManager Wake Locks:** Hooks partial wake locks on CPU cores to block sleep cycles while performing active session counting.
5.  **SharedPreferences State Persistence:** Automatically commits tracking and off-times to client storage. If the system restarts or terminates the service, the app seamlessly recovers context exactly where you were.
6.  **Edge-to-Edge Visual Design:** Implements `enableEdgeToEdge()` safe drawing, paired with a custom high-contrast Circular Countdown Gauge showing real-time states in comforting teal and mint shades.

---

## 🧪 Testing Mode (QA & Review)
The app is built with a direct **Test Mode Toggle** for immediate testing and QA reviews:
*   Activate it inside the **Configurations Settings** (accessed via the `3-Dot Menu` in the top-right corner).
*   **When Test Mode is ON:**
    *   The 20-minute interval becomes **20 seconds**.
    *   The 20-second resting break becomes **2 seconds**.
    *   The Screen-Off threshold becomes **2 seconds** (screen locks of >2 seconds reset the timer; screen locks of ≤2 seconds continue).
    *   All corresponding UI gauges, persistent notifications, and interactive alerts automatically recalibrate to these values in real-time.

---

## 🚀 How to Run and Test
1.  **Build and Run:** Run on any device or emulator running **Android 7.0 (API 24) or higher**.
2.  **Allow Notification Permissions:** On Android 13+, the app will politely ask for Notification Permissions when starting the tracker. This must be granted to see the eye rest reminder banner!
3.  **Ignore Battery Optimizations:** For uninterrupted countdown tracking under heavy system load, grant **Battery Exemption** by clicking on "Grant Permission" inside the configurations menu or on startup.
4.  **Local Unit & Screenshot Verification:**
    ```bash
    # Execute standard unit tests and robolectric configurations
    gradle :app:testDebugUnitTest
    ```
