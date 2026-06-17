package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimerState {
    INACTIVE,
    COUNTING,
    RESTING,
    PAUSED_BY_SCREEN_OFF
}

data class EyeCareState(
    val timerState: TimerState = TimerState.INACTIVE,
    val elapsedMs: Long = 0L,
    val totalDurationMs: Long = NORMAL_WORK_DURATION,
    val restDurationMs: Long = NORMAL_REST_DURATION,
    val isTestMode: Boolean = false
) {
    val remainingMs: Long
        get() = if (timerState == TimerState.RESTING) {
            (restDurationMs - elapsedMs).coerceAtLeast(0L)
        } else {
            (totalDurationMs - elapsedMs).coerceAtLeast(0L)
        }

    val progress: Float
        get() = if (timerState == TimerState.RESTING) {
            if (restDurationMs > 0) (elapsedMs.toFloat() / restDurationMs).coerceIn(0f, 1f) else 1f
        } else {
            if (totalDurationMs > 0) (elapsedMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 1f
        }
}

// Durations
const val NORMAL_WORK_DURATION = 20L * 60 * 1000 // 20 minutes
const val NORMAL_REST_DURATION = 20L * 1000 // 20 seconds

const val TEST_WORK_DURATION = 20L * 1000 // 20 seconds
const val TEST_REST_DURATION = 2L * 1000 // 2 seconds

class EyeCareService : Service() {

    companion object {
        private const val CHANNEL_ID = "eye_care_channel"
        private const val REMINDER_CHANNEL_ID = "eye_care_reminder_channel"
        private const val NOTIFICATION_ID = 4020
        private const val REMINDER_NOTIFICATION_ID = 4021

        private const val PREFS_NAME = "EyeCarePrefs"
        private const val KEY_ELAPSED = "saved_elapsed"
        private const val KEY_STATE = "saved_state"
        private const val KEY_TEST_MODE = "saved_test_mode"
        private const val KEY_SCREEN_OFF_TIME = "screen_off_timestamp"

        private val _state = MutableStateFlow(EyeCareState())
        val state: StateFlow<EyeCareState> = _state.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }

    private lateinit var prefs: SharedPreferences
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isReceiverRegistered = false

    // Timing helper properties using real-time deltas
    private var lastTickTime: Long = 0L

    private val timerRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isServiceRunning.value = true

        createNotificationChannels()
        registerScreenReceiver()
        acquireWakeLock()
        
        // Load initial state from settings
        val isTest = prefs.getBoolean(KEY_TEST_MODE, false)
        val savedStateStr = prefs.getString(KEY_STATE, TimerState.INACTIVE.name) ?: TimerState.INACTIVE.name
        val savedState = try {
            TimerState.valueOf(savedStateStr)
        } catch (e: Exception) {
            TimerState.INACTIVE
        }
        val savedElapsed = prefs.getLong(KEY_ELAPSED, 0L)

        val total = if (isTest) TEST_WORK_DURATION else NORMAL_WORK_DURATION
        val rest = if (isTest) TEST_REST_DURATION else NORMAL_REST_DURATION

        // If service was killed and restarted, recover where we were
        val restoredState = if (savedState == TimerState.PAUSED_BY_SCREEN_OFF) {
            TimerState.PAUSED_BY_SCREEN_OFF
        } else if (savedState == TimerState.COUNTING || savedState == TimerState.RESTING) {
            savedState
        } else {
            TimerState.COUNTING
        }

        _state.value = EyeCareState(
            timerState = restoredState,
            elapsedMs = savedElapsed,
            totalDurationMs = total,
            restDurationMs = rest,
            isTestMode = isTest
        )

        lastTickTime = SystemClock.elapsedRealtime()
        handler.post(timerRunnable)

        // Go foreground straight away
        val notification = buildServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.hasExtra("COMMAND")) {
                when (it.getStringExtra("COMMAND")) {
                    "STOP" -> {
                        stopTracking()
                        return START_NOT_STICKY
                    }
                    "TOGGLE_TEST_MODE" -> {
                        toggleTestMode(it.getBooleanExtra("IS_TEST", false))
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EyeCare::ServiceWakeLock").apply {
                acquire(24 * 60 * 60 * 1000L) // Safe limit
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            wakeLock = null
        }
    }

    private fun registerScreenReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenReceiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun unregisterScreenReceiver() {
        if (isReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            isReceiverRegistered = false
        }
    }

    private fun handleScreenOff() {
        val currentState = _state.value
        if (currentState.timerState == TimerState.COUNTING) {
            // Save current count state and transition
            prefs.edit().apply {
                putLong(KEY_ELAPSED, currentState.elapsedMs)
                putString(KEY_STATE, TimerState.PAUSED_BY_SCREEN_OFF.name)
                putLong(KEY_SCREEN_OFF_TIME, System.currentTimeMillis())
                apply()
            }
            _state.value = currentState.copy(timerState = TimerState.PAUSED_BY_SCREEN_OFF)
            updateNotification()
        }
    }

    private fun handleScreenOn() {
        val currentState = _state.value
        if (currentState.timerState == TimerState.PAUSED_BY_SCREEN_OFF) {
            val screenOffTimestamp = prefs.getLong(KEY_SCREEN_OFF_TIME, 0L)
            if (screenOffTimestamp > 0L) {
                val offDuration = System.currentTimeMillis() - screenOffTimestamp
                
                // If Test Mode is ON, threshold is 2 seconds (2000 ms). Otherwise, 20 seconds (20,000 ms)
                val threshold = if (currentState.isTestMode) 2000L else 20000L

                val nextElapsed = if (offDuration <= threshold) {
                    currentState.elapsedMs // Continue from left-off
                } else {
                    0L // Reset to zero
                }

                _state.value = currentState.copy(
                    timerState = TimerState.COUNTING,
                    elapsedMs = nextElapsed
                )
                
                prefs.edit().apply {
                    putLong(KEY_ELAPSED, nextElapsed)
                    putString(KEY_STATE, TimerState.COUNTING.name)
                    putLong(KEY_SCREEN_OFF_TIME, 0L)
                    apply()
                }

                lastTickTime = SystemClock.elapsedRealtime()
                updateNotification()
            }
        }
    }

    private fun toggleTestMode(isTest: Boolean) {
        val currentState = _state.value
        val total = if (isTest) TEST_WORK_DURATION else NORMAL_WORK_DURATION
        val rest = if (isTest) TEST_REST_DURATION else NORMAL_REST_DURATION
        
        // Force reset the active timer cleanly to avoid state jumps
        _state.value = currentState.copy(
            timerState = TimerState.COUNTING,
            elapsedMs = 0L,
            totalDurationMs = total,
            restDurationMs = rest,
            isTestMode = isTest
        )

        prefs.edit().apply {
            putBoolean(KEY_TEST_MODE, isTest)
            putLong(KEY_ELAPSED, 0L)
            putString(KEY_STATE, TimerState.COUNTING.name)
            apply()
        }

        lastTickTime = SystemClock.elapsedRealtime()
        updateNotification()
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastTickTime
        lastTickTime = now

        val currentState = _state.value
        when (currentState.timerState) {
            TimerState.COUNTING -> {
                val nextElapsed = currentState.elapsedMs + delta
                if (nextElapsed >= currentState.totalDurationMs) {
                    // Transition to Rest!
                    _state.value = currentState.copy(
                        timerState = TimerState.RESTING,
                        elapsedMs = 0L
                    )
                    prefs.edit().apply {
                        putString(KEY_STATE, TimerState.RESTING.name)
                        putLong(KEY_ELAPSED, 0L)
                        apply()
                    }
                    showRestReminderNotification()
                } else {
                    _state.value = currentState.copy(elapsedMs = nextElapsed)
                    // Periodic save (optional, let's keep it healthy)
                    prefs.edit().putLong(KEY_ELAPSED, nextElapsed).apply()
                }
                updateNotification()
            }
            TimerState.RESTING -> {
                val nextElapsed = currentState.elapsedMs + delta
                if (nextElapsed >= currentState.restDurationMs) {
                    // Back to counting!
                    _state.value = currentState.copy(
                        timerState = TimerState.COUNTING,
                        elapsedMs = 0L
                    )
                    prefs.edit().apply {
                        putString(KEY_STATE, TimerState.COUNTING.name)
                        putLong(KEY_ELAPSED, 0L)
                        apply()
                    }
                } else {
                    _state.value = currentState.copy(elapsedMs = nextElapsed)
                }
                updateNotification()
            }
            else -> {
                // Suspended, no delta tracking
            }
        }
    }

    private fun showRestReminderNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (_state.value.isTestMode) {
            "Time to rest your eyes (2 seconds)"
        } else {
            "Time to rest your eyes (20 seconds)"
        }

        val notification = NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_online) // temporary icon choice
            .setContentTitle("Eye Comfort Break")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(5000) // Auto-dismisses in 5 seconds
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(REMINDER_NOTIFICATION_ID, notification)
    }

    private fun buildServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, EyeCareService::class.java).apply {
            putExtra("COMMAND", "STOP")
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentState = _state.value
        val text = when (currentState.timerState) {
            TimerState.COUNTING -> {
                val remainingMs = currentState.remainingMs
                val min = remainingMs / 1000 / 60
                val sec = (remainingMs / 1000) % 60
                "20-20-20 Active: [${min}m ${sec}s remaining]"
            }
            TimerState.RESTING -> {
                "Time to rest! Eyes relaxing..."
            }
            TimerState.PAUSED_BY_SCREEN_OFF -> {
                "20-20-20 Paused (Screen Locked)"
            }
            else -> {
                "20-20-20 Eye Care Service"
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentTitle("20-20-20 Protection")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildServiceNotification())
    }

    private fun stopTracking() {
        _state.value = EyeCareState()
        prefs.edit().apply {
            putString(KEY_STATE, TimerState.INACTIVE.name)
            putLong(KEY_ELAPSED, 0L)
            putLong(KEY_SCREEN_OFF_TIME, 0L)
            apply()
        }
        stopSelf()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Persistent Status Info",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows dynamic remaining eye care minutes"
                setShowBadge(false)
            }

            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Eye Break Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up alert reminding eyes to look 20 feet away"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(reminderChannel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        unregisterScreenReceiver()
        releaseWakeLock()
        _isServiceRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
