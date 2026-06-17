package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val serviceState by EyeCareService.state.collectAsStateWithLifecycle()
    val isRunning by EyeCareService.isServiceRunning.collectAsStateWithLifecycle()

    var showMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showBatteryExplanationDialog by remember { mutableStateOf(false) }

    // Dynamic checks
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(false) }
    LaunchedEffect(Unit, isRunning) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // Permission launcher for Android 13+ Notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startTrackingService(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "20-20-20 Eye Care",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu Options",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Settings")
                                }
                            },
                            onClick = {
                                showMenu = false
                                showSettingsDialog = true
                            }
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Cohesive Forest Cover illustration from generated resources
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.eye_care_banner),
                    contentDescription = "Beautiful Calming Forest Scenic Visual for Restful Eyes",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Subtle shadow overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "Step away from screen fatigue",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Calming rules for your visual wellness.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Elegant Gauge Display container
            ActiveTimerGauge(
                state = serviceState,
                isRunning = isRunning
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Big glowing Master Start / Stop button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        if (isRunning) {
                            stopTrackingService(context)
                        } else {
                            handleStartCycle(
                                context = context,
                                needNotificationPermission = Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED,
                                ignoreBatteryPrompt = !isIgnoringBatteryOptimizations,
                                onLaunchPermission = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onShowBatteryPrompt = {
                                    showBatteryExplanationDialog = true
                                },
                                onTrackingAllowed = {
                                    startTrackingService(context)
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) {
                            if (serviceState.timerState == TimerState.RESTING) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier
                        .widthIn(min = 200.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    val label = when {
                        !isRunning -> "START"
                        serviceState.timerState == TimerState.RESTING -> "REST ACTIVE"
                        else -> "STOP"
                    }
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Informative descriptive explanation card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "What is the 20-20-20 Rule?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To keep your eyes from straining while working on computers, tablets, or phones: for every 20 minutes you stare at a screen, focus on an object at least 20 feet away for at least 20 seconds.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 18.sp
                    )
                }
            }

            // Small active test badge for safety
            AnimatedVisibility(
                visible = serviceState.isTestMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(symmetricPadding(8.dp, 12.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Test Mode Active (20s cycle / 2s rest)",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Settings popup dialog (clean Material 3 custom dialogue)
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "Configurations",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Test Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Shortens cycles to 20s and rest to 2s for easy testing.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = serviceState.isTestMode,
                            onCheckedChange = { isChecked ->
                                toggleTestModeCommand(context, isChecked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Battery Exemption",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                if (isIgnoringBatteryOptimizations) "Granted: Background safe" else "Recommended to prevent system sleep",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            if (!isIgnoringBatteryOptimizations) {
                                TextButton(
                                    onClick = {
                                        showSettingsDialog = false
                                        showBatteryExplanationDialog = true
                                    },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("Grant Permission", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("CLOSE")
                }
            }
        )
    }

    // Battery Optimization Explanation Dialog
    if (showBatteryExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryExplanationDialog = false },
            title = {
                Text("Battery Exemption Required")
            },
            text = {
                Text("To accurately track your 20-minute screen usage intervals in the background while your phone is sleep or locked, we require permission to ignore system battery optimization kills. Please choose 'Allow' on the next screen.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatteryExplanationDialog = false
                        requestBatteryOptimizations(context)
                    }
                ) {
                    Text("PROCEED")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryExplanationDialog = false }) {
                    Text("LATER")
                }
            }
        )
    }
}

@Composable
fun ActiveTimerGauge(
    state: EyeCareState,
    isRunning: Boolean
) {
    val progressAnimated by animateFloatAsState(
        targetValue = if (isRunning) state.progress else 0f,
        label = "Progress"
    )

    val gaugeColor by animateColorAsState(
        targetValue = when {
            !isRunning -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            state.timerState == TimerState.RESTING -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "GaugeColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(260.dp)
    ) {
        // Background ring path
        CircularProgressIndicator(
            progress = { 1.0f },
            modifier = Modifier.size(240.dp),
            color = MaterialTheme.colorScheme.surface,
            strokeWidth = 14.dp
        )

        // Dynamic active countdown/count-up path
        CircularProgressIndicator(
            progress = { progressAnimated },
            modifier = Modifier.size(240.dp),
            color = gaugeColor,
            strokeWidth = 14.dp,
            strokeCap = StrokeCap.Round
        )

        // Inner stats column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            val labelText = when {
                !isRunning -> "READY"
                state.timerState == TimerState.RESTING -> "RELAXING"
                state.timerState == TimerState.PAUSED_BY_SCREEN_OFF -> "PAUSED"
                else -> "ACTIVE"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(gaugeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = labelText,
                    color = gaugeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time Remaining
            val remainingSec = state.remainingMs / 1000
            val mins = remainingSec / 60
            val secs = remainingSec % 60
            val clockText = String.format("%02d:%02d", mins, secs)

            Text(
                text = if (isRunning) clockText else "--:--",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Text guide prompt
            val promptText = when {
                !isRunning -> "Tap Start to Care"
                state.timerState == TimerState.RESTING -> "Look 20 feet away"
                state.timerState == TimerState.PAUSED_BY_SCREEN_OFF -> "Resume on Unlock"
                else -> "Time until break"
            }

            Text(
                text = promptText,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Layout helper for modifiers
private fun symmetricPadding(vertical: androidx.compose.ui.unit.Dp, horizontal: androidx.compose.ui.unit.Dp): androidx.compose.foundation.layout.PaddingValues {
    return androidx.compose.foundation.layout.PaddingValues(
        start = horizontal,
        end = horizontal,
        top = vertical,
        bottom = vertical
    )
}

private fun handleStartCycle(
    context: Context,
    needNotificationPermission: Boolean,
    ignoreBatteryPrompt: Boolean,
    onLaunchPermission: () -> Unit,
    onShowBatteryPrompt: () -> Unit,
    onTrackingAllowed: () -> Unit
) {
    if (needNotificationPermission) {
        onLaunchPermission()
    } else if (ignoreBatteryPrompt) {
        onShowBatteryPrompt()
    } else {
        onTrackingAllowed()
    }
}

private fun startTrackingService(context: Context) {
    val intent = Intent(context, EyeCareService::class.java)
    ContextCompat.startForegroundService(context, intent)
}

private fun stopTrackingService(context: Context) {
    val intent = Intent(context, EyeCareService::class.java).apply {
        putExtra("COMMAND", "STOP")
    }
    context.startService(intent)
}

private fun toggleTestModeCommand(context: Context, isTest: Boolean) {
    val intent = Intent(context, EyeCareService::class.java).apply {
        putExtra("COMMAND", "TOGGLE_TEST_MODE")
        putExtra("IS_TEST", isTest)
    }
    context.startService(intent)
}

private fun requestBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to general settings if request not directly allowed on standard device profiles
        try {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {}
    }
}
