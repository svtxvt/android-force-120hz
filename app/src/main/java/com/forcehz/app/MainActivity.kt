package com.forcehz.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private var fpsReceiver: BroadcastReceiver? = null
    private val currentFps = mutableFloatStateOf(0f)
    private val isServiceEnabled = mutableStateOf(false)
    private val isAnimationActive = mutableStateOf(false)
    private val currentMode = mutableIntStateOf(0)
    private val displayRefreshRate = mutableIntStateOf(120)
    
    private val batteryTemp = mutableFloatStateOf(0f)
    private val batteryLevel = mutableIntStateOf(0)
    private val isBatteryOptimizationDisabled = mutableStateOf(false)
    private var monitorHandler: Handler? = null
    private var monitorRunnable: Runnable? = null

    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshState()

        setContent {
            ForceHzTheme {
                Force120HzApp(
                    currentFps = currentFps.floatValue,
                    isServiceEnabled = isServiceEnabled.value,
                    isAnimationActive = isAnimationActive.value,
                    currentMode = currentMode.intValue,
                    displayRefreshRate = displayRefreshRate.intValue,
                    batteryTemp = batteryTemp.floatValue,
                    batteryLevel = batteryLevel.intValue,
                    isBatteryOptimizationDisabled = isBatteryOptimizationDisabled.value,
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onOpenBatteryOptimizationSettings = { openBatteryOptimizationSettings() },
                    onOpenAutoStartSettings = { openAutoStartSettings() },
                    onOpenAppSettings = { openAppDetailsSettings() },
                    onToggleAnimation = { toggleAnimation() },
                    onToggleFpsOverlay = { toggleFpsOverlay() },
                    onChangeMode = { mode -> changeMode(mode) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshState()
        startMonitoring()
        registerFpsReceiver()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onStop() {
        super.onStop()
        unregisterFpsReceiver()
        stopMonitoring()
    }
    
    // ...
    
    private fun startMonitoring() {
        if (monitorHandler != null) return
        monitorHandler = Handler(Looper.getMainLooper())
        monitorRunnable = object : Runnable {
            override fun run() {
                updateBatteryInfo()
                monitorHandler?.postDelayed(this, 2000)
            }
        }
        monitorHandler?.post(monitorRunnable!!)
    }
    
    private fun stopMonitoring() {
        monitorRunnable?.let { monitorHandler?.removeCallbacks(it) }
        monitorRunnable = null
        monitorHandler = null
    }
    
    private fun updateBatteryInfo() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let {
            batteryTemp.floatValue = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            batteryLevel.intValue = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        }
    }
    

    
    private fun refreshState() {
        isServiceEnabled.value = isAccessibilityServiceEnabled()
        isAnimationActive.value = ForceHzAccessibilityService.instance?.isAnimationEnabled() ?: false
        currentMode.intValue = ForceHzAccessibilityService.instance?.getCurrentMode() 
            ?: getSharedPreferences("force_hz_prefs", MODE_PRIVATE).getInt("animation_mode", 0)
        displayRefreshRate.intValue = ForceHzAccessibilityService.instance?.getDisplayRefreshRate() ?: 120
        isBatteryOptimizationDisabled.value = checkBatteryOptimizationDisabled()
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
    
    private fun registerFpsReceiver() {
        if (fpsReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ForceHzAccessibilityService.ACTION_FPS_UPDATE) {
                    currentFps.floatValue = intent.getFloatExtra(ForceHzAccessibilityService.EXTRA_FPS, 0f)
                }
            }
        }
        fpsReceiver = receiver
        val filter = IntentFilter(ForceHzAccessibilityService.ACTION_FPS_UPDATE)
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    
    private fun unregisterFpsReceiver() {
        fpsReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) { } }
        fpsReceiver = null
        currentFps.floatValue = 0f
    }

    private fun openAccessibilitySettings() {
        accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, "Find 'Force 120Hz' and enable it", Toast.LENGTH_LONG).show()
    }

    private fun checkBatteryOptimizationDisabled(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings()
        }
    }

    private fun openAutoStartSettings() {
        val candidates = listOf(
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.oplus.safecenter",
                "com.oplus.safecenter.startupapp.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ),
            Intent().setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        )

        val launched = candidates.firstOrNull { intent ->
            intent.resolveActivity(packageManager) != null
        }?.let { intent ->
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } ?: false

        if (!launched) {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
    
    private fun toggleAnimation() {
        val service = ForceHzAccessibilityService.instance
        if (service != null) {
            if (isAnimationActive.value) service.stopForceRefresh()
            else service.startForceRefresh()
            isAnimationActive.value = !isAnimationActive.value
        } else {
            openAccessibilitySettings()
        }
    }
    
    private fun toggleFpsOverlay() {
        ForceHzAccessibilityService.instance?.toggleFpsOverlay()
    }
    
    private fun changeMode(mode: Int) {
        currentMode.intValue = mode
        ForceHzAccessibilityService.instance?.changeMode(mode)
            ?: getSharedPreferences("force_hz_prefs", MODE_PRIVATE).edit {
                putInt("animation_mode", mode)
            }
    }
}

@Composable
fun ForceHzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00E676),
            onPrimary = Color.Black,
            surface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFF2C2C2E),
            background = Color(0xFF0A0A0C),
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFB0B0B0)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Force120HzApp(
    currentFps: Float,
    isServiceEnabled: Boolean,
    isAnimationActive: Boolean,
    currentMode: Int,
    displayRefreshRate: Int,
    batteryTemp: Float,
    batteryLevel: Int,
    isBatteryOptimizationDisabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenAutoStartSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onToggleAnimation: () -> Unit,
    onToggleFpsOverlay: () -> Unit,
    onChangeMode: (Int) -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("force_hz_prefs", Context.MODE_PRIVATE)
    var showFpsOverlay by remember { mutableStateOf(prefs.getBoolean("show_fps", false)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚡ Force ${displayRefreshRate}Hz", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            FpsCard(currentFps, isAnimationActive && isServiceEnabled, onToggleAnimation)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            StatsCard(batteryTemp, batteryLevel)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Optimized Animation", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("• Choreographer + VSync synchronization\n• postInvalidateOnAnimation()\n• Minimal onDraw < 8ms", 
                         fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FPS Overlay", fontSize = 14.sp)
                        Switch(
                            checked = showFpsOverlay,
                            onCheckedChange = { 
                                showFpsOverlay = it
                                onToggleFpsOverlay()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF3C3C3E))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAccessibilitySettings() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔧", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Accessibility Service", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        
                        if (isServiceEnabled) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        } else {
                            Text("→", fontSize = 16.sp, color = Color(0xFFFF9800))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            StabilityCard(
                isServiceEnabled = isServiceEnabled,
                isBatteryOptimizationDisabled = isBatteryOptimizationDisabled,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                onOpenAutoStartSettings = onOpenAutoStartSettings,
                onOpenAppSettings = onOpenAppSettings
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StabilityCard(
    isServiceEnabled: Boolean,
    isBatteryOptimizationDisabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenAutoStartSettings: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val okColor = Color(0xFF00E676)
    val warnColor = Color(0xFFFFB74D)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Startup Stability", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "For always-on 120Hz after reboot, keep Accessibility enabled, battery unrestricted, and OEM auto-start allowed.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            StabilityStatusRow(
                label = "Accessibility Service",
                value = if (isServiceEnabled) "Enabled" else "Disabled",
                valueColor = if (isServiceEnabled) okColor else warnColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            StabilityStatusRow(
                label = "Battery Mode",
                value = if (isBatteryOptimizationDisabled) "Unrestricted" else "Optimized",
                valueColor = if (isBatteryOptimizationDisabled) okColor else warnColor
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Auto-start status cannot be read on most OEM ROMs. Open OEM Auto-start and allow this app manually.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onOpenAutoStartSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open OEM Auto-start")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenBatteryOptimizationSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Battery Settings")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Accessibility Settings")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenAppSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open App Details")
            }
        }
    }
}

@Composable
fun StabilityStatusRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
fun FpsCard(currentFps: Float, isActive: Boolean, onToggle: () -> Unit) {
    val fpsColor by animateColorAsState(
        when {
            !isActive -> Color.Gray
            currentFps >= 115 -> Color(0xFF00E676)
            currentFps >= 85 -> Color(0xFFFFEB3B)
            currentFps > 0 -> Color(0xFFFF5252)
            else -> Color.Gray
        }, label = "fps"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth().scale(if (isActive) 1f else 0.97f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (isActive && currentFps > 0) "%.0f".format(currentFps) else "—",
                    fontSize = 56.sp, fontWeight = FontWeight.Bold, color = fpsColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("FPS", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                     modifier = Modifier.padding(bottom = 10.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow, null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isActive) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (isActive) "Active" else "Disabled",
                fontSize = 12.sp,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatsCard(batteryTemp: Float, batteryLevel: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("🌡️", "%.1f°C".format(batteryTemp), "Temp",
                when { batteryTemp >= 42 -> Color(0xFFFF5252); batteryTemp >= 38 -> Color(0xFFFFEB3B); else -> Color(0xFF00E676) })
            StatItem("🔋", "$batteryLevel%", "Battery",
                when { batteryLevel <= 20 -> Color(0xFFFF5252); batteryLevel <= 50 -> Color(0xFFFFEB3B); else -> Color(0xFF00E676) })
        }
    }
}

@Composable
fun StatItem(emoji: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
