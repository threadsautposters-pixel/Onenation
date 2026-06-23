package com.onenation.recommender

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object C {
    val Bg = Color(0xFF09111F)
    val Surface = Color(0xFF0F172A)
    val Card = Color(0xFF182235)
    val CardAlt = Color(0xFF101A2B)
    val Border = Color(0xFF25334A)
    val Green = Color(0xFF10B981)
    val GreenDim = Color(0xFF10B981).copy(alpha = 0.16f)
    val Blue = Color(0xFF3B82F6)
    val BlueDim = Color(0xFF3B82F6).copy(alpha = 0.16f)
    val Orange = Color(0xFFF59E0B)
    val OrangeDim = Color(0xFFF59E0B).copy(alpha = 0.16f)
    val Red = Color(0xFFEF4444)
    val RedDim = Color(0xFFEF4444).copy(alpha = 0.16f)
    val Purple = Color(0xFF8B5CF6)
    val Cyan = Color(0xFF06B6D4)
    val Text1 = Color(0xFFF8FAFC)
    val Text2 = Color(0xFFB8C5D6)
    val Text3 = Color(0xFF71839B)
}

class MainActivity : ComponentActivity() {
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (!granted.values.all { it }) {
                Toast.makeText(this, "Required permissions were not fully granted", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val needed = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = C.Green,
                    secondary = C.Blue,
                    background = C.Bg,
                    surface = C.Surface,
                ),
            ) {
                App()
            }
        }

        if (missing.isNotEmpty()) {
            window.decorView.post {
                requestPermissionsLauncher.launch(missing.toTypedArray())
            }
        }
    }
}

@Composable
fun App() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Dashboard" to Icons.Filled.Dashboard,
        "Saved" to Icons.Outlined.Group,
        "Installed" to Icons.Filled.CheckCircle,
        "Logs" to Icons.Filled.ListAlt,
        "Settings" to Icons.Filled.Settings,
    )

    Scaffold(
        containerColor = C.Bg,
        bottomBar = {
            NavigationBar(
                containerColor = C.Surface,
                modifier = Modifier.height(70.dp),
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, label, Modifier.size(22.dp)) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.Green,
                            selectedTextColor = C.Green,
                            unselectedIconColor = C.Text3,
                            unselectedTextColor = C.Text3,
                            indicatorColor = C.GreenDim,
                        ),
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            when (selectedTab) {
                0 -> Dash()
                1 -> Saved()
                2 -> Installed()
                3 -> Logs()
                else -> Settings()
            }
        }
    }
}

@Composable
fun Dash() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    var run by remember { mutableStateOf(RecommendationService.isRunning) }
    var sc by remember { mutableIntStateOf(RecommendationService.successCount) }
    var ta by remember { mutableIntStateOf(RecommendationService.totalAttempts) }
    var fc by remember { mutableIntStateOf(RecommendationService.failedCount) }
    var ic by remember { mutableIntStateOf(RecommendationService.installedCount) }
    var ll by remember { mutableStateOf(RecommendationService.lastLog) }
    var pc by remember { mutableIntStateOf(ContactManager.getPendingCount(ctx)) }
    var si by remember { mutableIntStateOf(ContactManager.getInstalledCount(ctx)) }
    var lt by remember { mutableLongStateOf(ContactManager.getLifetimeTotal(ctx)) }
    var ls by remember { mutableLongStateOf(ContactManager.getLifetimeSuccess(ctx)) }
    var gc by remember { mutableLongStateOf(ContactManager.getGeneratedCount(ctx)) }
    var dailyTarget by remember { mutableIntStateOf(prefs.getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET)) }
    var pauseRemainingMs by remember { mutableLongStateOf(AutomationPauseManager.getRemainingPauseMs(ctx)) }
    var callInProgress by remember { mutableStateOf(CallStateManager.isCallInProgress(ctx)) }

    fun refreshState() {
        run = RecommendationService.isRunning
        sc = RecommendationService.successCount
        ta = RecommendationService.totalAttempts
        fc = RecommendationService.failedCount
        ic = RecommendationService.installedCount
        ll = RecommendationService.lastLog
        pc = ContactManager.getPendingCount(ctx)
        si = ContactManager.getInstalledCount(ctx)
        lt = ContactManager.getLifetimeTotal(ctx)
        ls = ContactManager.getLifetimeSuccess(ctx)
        gc = ContactManager.getGeneratedCount(ctx)
        dailyTarget = prefs.getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET)
        pauseRemainingMs = AutomationPauseManager.getRemainingPauseMs(ctx)
        callInProgress = CallStateManager.isCallInProgress(ctx)
    }

    DisposableEffect(Unit) {
        RecommendationService.onUpdate = { refreshState() }
        onDispose { RecommendationService.onUpdate = null }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            refreshState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(
            title = "One Nation",
            subtitle = "Professional automation dashboard for recommendations",
        )

        HeroStatusCard(
            isRunning = run,
            simLabel = SimSelection.getSelectedSimLabel(ctx),
            dailyTarget = dailyTarget,
            lastLog = ll,
            pauseRemainingMs = pauseRemainingMs,
            callInProgress = callInProgress,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryActionButton(
                modifier = Modifier.weight(1f),
                label = if (run) "Stop Automation" else "Start Automation",
                icon = if (run) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                color = if (run) C.Red else C.Green,
            ) {
                if (run) {
                    ctx.startService(
                        Intent(ctx, RecommendationService::class.java).apply {
                            action = RecommendationService.ACT_STOP
                        },
                    )
                } else {
                    startRecommendationService(ctx)
                }
                vibrate(ctx)
                refreshState()
            }

            SecondaryActionButton(
                modifier = Modifier.weight(1f),
                label = "Import Contacts",
                icon = Icons.Outlined.Contacts,
                color = C.Blue,
            ) {
                importContacts(ctx)
                refreshState()
            }
        }

        SectionTitle("Session Overview", "Current run performance and outcomes")
        MetricsGrid(
            items = listOf(
                MetricItem("Attempts", "$ta", C.Blue),
                MetricItem("Success", "$sc", C.Green),
                MetricItem("Failed", "$fc", C.Red),
                MetricItem("Installed", "$ic", C.Orange),
            ),
        )

        SectionTitle("Lifetime Overview", "All-time totals stored on this device")
        MetricsGrid(
            items = listOf(
                MetricItem("Total Saved", "$lt", C.Purple),
                MetricItem("Confirmed", "$ls", C.Cyan),
                MetricItem("Generated", "$gc", C.Blue),
                MetricItem("Pending", "$pc", C.Orange),
                MetricItem("Installed", "$si", C.Green),
                MetricItem("Daily Target", "$dailyTarget", C.Text1),
                MetricItem(
                    "Pause Left",
                    if (pauseRemainingMs > 0L) AutomationPauseManager.describeRemaining(pauseRemainingMs) else "0s",
                    C.Red,
                ),
                MetricItem("Call State", if (callInProgress) "Busy" else "Idle", C.Orange),
            ),
        )

        SectionCard {
            Text("Background Operation", color = C.Text1, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "When automation is started it keeps running in the background with a foreground notification, and the service resumes after reboot if it was active.",
                color = C.Text2,
                fontSize = 13.sp,
            )
        }
    }
}

data class MetricItem(val label: String, val value: String, val color: Color)

@Composable
fun MetricsGrid(items: List<MetricItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { item ->
                    MetricCard(item = item, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun MetricCard(item: MetricItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = C.Card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.label, fontSize = 12.sp, color = C.Text3)
            Spacer(Modifier.height(8.dp))
            Text(item.value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = item.color)
        }
    }
}

@Composable
fun HeroStatusCard(
    isRunning: Boolean,
    simLabel: String,
    dailyTarget: Int,
    lastLog: String,
    pauseRemainingMs: Long,
    callInProgress: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = C.CardAlt),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(C.Blue.copy(alpha = 0.18f), C.CardAlt),
                    ),
                )
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    label = when {
                        pauseRemainingMs > 0L -> "Paused"
                        callInProgress -> "On Call"
                        isRunning -> "Running"
                        else -> "Stopped"
                    },
                    color = when {
                        pauseRemainingMs > 0L -> C.Orange
                        callInProgress -> C.Orange
                        isRunning -> C.Green
                        else -> C.Red
                    },
                )
                StatusPill(label = simLabel, color = C.Blue)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    pauseRemainingMs > 0L ->
                        "Automation resumes in ${AutomationPauseManager.describeRemaining(pauseRemainingMs)}"
                    callInProgress -> "Automation waits until the current call has finished"
                    isRunning -> "Automation is active in the background"
                    else -> "Automation is currently paused"
                },
                color = C.Text1,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (pauseRemainingMs > 0L) {
                    "M-Pesa activity triggered a temporary hold to avoid interrupting your other automation."
                } else if (callInProgress) {
                    "Incoming or ongoing calls pause recommendations automatically until the line is idle."
                } else {
                    "Daily target is set to $dailyTarget and requests are processed every 8 seconds."
                },
                color = C.Text2,
                fontSize = 13.sp,
            )
            if (lastLog.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Latest activity", color = C.Text3, fontSize = 11.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(lastLog, color = C.Text1, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.Text1)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 13.sp, color = C.Text2)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.Text1)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = C.Text3)
    }
}

@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = C.Card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun PrimaryActionButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryActionButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Saved() {
    val ctx = LocalContext.current
    var nums by remember { mutableStateOf(ContactManager.getPending(ctx)) }
    var del by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            nums = ContactManager.getPending(ctx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        ScreenTitle("Saved Numbers", "${nums.size} pending numbers waiting for follow-up")
        Spacer(Modifier.height(16.dp))

        if (nums.isEmpty()) {
            Emp(Icons.Outlined.Group, "No saved numbers", "Numbers waiting for retry will appear here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(nums, key = { it.phone }) { number ->
                    val sourceColor = when {
                        number.source.contains("MPESA") -> C.Green
                        number.source.contains("CONTACTS") -> C.Orange
                        else -> C.Blue
                    }
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(number.phone, color = C.Text1, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                StatusPill(number.source.replace("_", " "), sourceColor)
                                Spacer(Modifier.height(8.dp))
                                DetailLine("Added", "${number.dateAdded} ${number.timeAdded}")
                                DetailLine(
                                    "Last attempt",
                                    number.lastAttempted.ifBlank { "Not attempted yet" },
                                )
                                DetailLine(
                                    "Next retry",
                                    number.nextRetryTime.ifBlank { "Waiting for first run" },
                                )
                                DetailLine("Retry count", "${number.retryCount}")
                            }
                            IconButton(onClick = { del = number.phone }) {
                                Icon(Icons.Outlined.Delete, "Remove saved number", tint = C.Text3)
                            }
                        }
                    }
                }
            }
        }
    }

    del?.let { phone ->
        AlertDialog(
            onDismissRequest = { del = null },
            containerColor = C.Card,
            title = { Text("Remove saved number?", color = C.Text1) },
            text = { Text(phone, color = C.Text2) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ContactManager.deleteNumber(ctx, phone)
                        nums = ContactManager.getPending(ctx)
                        del = null
                    },
                ) {
                    Text("Remove", color = C.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { del = null }) {
                    Text("Cancel", color = C.Text2)
                }
            },
        )
    }
}

@Composable
fun Installed() {
    val ctx = LocalContext.current
    var nums by remember { mutableStateOf(ContactManager.getInstalled(ctx)) }
    var clr by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            nums = ContactManager.getInstalled(ctx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenTitle("Installed", "${nums.size} confirmed installations")
            if (nums.isNotEmpty()) {
                TextButton(onClick = { clr = true }) {
                    Text("Clear All", color = C.Red)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (nums.isEmpty()) {
            Emp(Icons.Outlined.CheckCircle, "No installed numbers", "Confirmed installs will be listed here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(nums, key = { it.phone }) { number ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = C.GreenDim),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = C.Green,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(number.phone, color = C.Text1, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("Added ${number.dateAdded} ${number.timeAdded}", color = C.Text2, fontSize = 12.sp)
                                Text(number.source.replace("_", " "), color = C.Green, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (clr) {
        AlertDialog(
            onDismissRequest = { clr = false },
            containerColor = C.Card,
            title = { Text("Clear installed list?", color = C.Text1) },
            text = { Text("This will remove ${nums.size} installed records.", color = C.Text2) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ContactManager.deleteInstalledNumbers(ctx)
                        nums = emptyList()
                        clr = false
                    },
                ) {
                    Text("Clear", color = C.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { clr = false }) {
                    Text("Cancel", color = C.Text2)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Logs() {
    val ctx = LocalContext.current
    var logs by remember { mutableStateOf(loadLogs(ctx)) }
    var filter by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            logs = loadLogs(ctx)
        }
    }

    val filteredLogs = when (filter) {
        "ALL" -> logs
        "USSD" -> logs.filter { it.contains("USSD RESPONSE") || it.contains("USSD ERROR") }
        else -> logs.filter { it.contains(filter) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        ScreenTitle("Activity Logs", "${filteredLogs.size} log entries")
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("ALL", "SUCCESS", "INSTALLED", "FAILED", "ERROR", "USSD").forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (option) {
                            "SUCCESS" -> C.GreenDim
                            "INSTALLED" -> C.OrangeDim
                            "FAILED" -> C.BlueDim
                            "ERROR" -> C.RedDim
                            "USSD" -> C.Purple.copy(alpha = 0.16f)
                            else -> Color.White.copy(alpha = 0.06f)
                        },
                        selectedLabelColor = when (option) {
                            "SUCCESS" -> C.Green
                            "INSTALLED" -> C.Orange
                            "FAILED" -> C.Blue
                            "ERROR" -> C.Red
                            "USSD" -> C.Purple
                            else -> C.Text2
                        },
                    ),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (filteredLogs.isEmpty()) {
            Emp(Icons.Outlined.ListAlt, "No logs yet", "New automation activity will appear here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredLogs) { log ->
                    val logColor = when {
                        log.contains("SUCCESS") -> C.Green
                        log.contains("INSTALLED") -> C.Orange
                        log.contains("ERROR") -> C.Red
                        log.contains("FAILED") -> C.Blue
                        log.contains("USSD") -> C.Purple
                        else -> C.Text2
                    }
                    SectionCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(logColor),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(log, color = logColor, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    var dailyTargetInput by remember {
        mutableStateOf(prefs.getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET).toString())
    }
    var autoDeleteSelection by remember { mutableStateOf(ContactManager.getAutoDeleteSetting(ctx)) }
    var clr by remember { mutableStateOf(false) }
    var simOptions by remember { mutableStateOf(SimSelection.getAvailableSimOptions(ctx)) }
    var simId by remember { mutableIntStateOf(SimSelection.getStoredSubscriptionId(ctx)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Settings", "Well-labelled controls with save buttons for each section")

        SectionCard {
            Text("Daily Target", color = C.Text1, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("Default is 7000 and automation stops when this total is reached for the day.", color = C.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = dailyTargetInput,
                onValueChange = {
                    if (it.all(Char::isDigit)) {
                        dailyTargetInput = it
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Daily target", color = C.Text3) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = C.Text1, fontSize = 16.sp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.Green,
                    unfocusedBorderColor = C.Border,
                    focusedTextColor = C.Text1,
                    unfocusedTextColor = C.Text1,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val value = dailyTargetInput.toIntOrNull() ?: DEFAULT_DAILY_TARGET
                    prefs.edit().putInt(KEY_DAILY_TARGET, value).apply()
                    dailyTargetInput = value.toString()
                    Toast.makeText(ctx, "Daily target saved", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Green),
            ) {
                Text("Save Daily Target", color = Color.White)
            }
        }

        SectionCard {
            Text("Automation SIM", color = C.Text1, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("Choose which SIM sends the USSD request.", color = C.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            DetailLine("Current selection", SimSelection.getSelectedSimLabel(ctx))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = simId == SimSelection.AUTO_SUBSCRIPTION_ID,
                    onClick = { simId = SimSelection.AUTO_SUBSCRIPTION_ID },
                    label = { Text("Auto", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = C.GreenDim,
                        selectedLabelColor = C.Green,
                    ),
                )
                simOptions.forEach { option ->
                    FilterChip(
                        selected = simId == option.subscriptionId,
                        onClick = { simId = option.subscriptionId },
                        label = { Text(option.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = C.GreenDim,
                            selectedLabelColor = C.Green,
                        ),
                    )
                }
            }
            if (simOptions.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "No active SIM detected. Auto mode uses the phone default line.",
                    color = C.Text3,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    SimSelection.saveSelectedSubscriptionId(ctx, simId)
                    simOptions = SimSelection.getAvailableSimOptions(ctx)
                    Toast.makeText(ctx, "SIM selection saved", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Green),
            ) {
                Text("Save SIM Choice", color = Color.White)
            }
        }

        SectionCard {
            Text("Auto Delete", color = C.Text1, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("Choose when installed records should be cleaned up automatically.", color = C.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("never", "daily", "weekly", "monthly").forEach { option ->
                    FilterChip(
                        selected = autoDeleteSelection == option,
                        onClick = { autoDeleteSelection = option },
                        label = { Text(option.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = C.GreenDim,
                            selectedLabelColor = C.Green,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    ContactManager.setAutoDeleteSetting(ctx, autoDeleteSelection)
                    Toast.makeText(ctx, "Auto delete setting saved", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Green),
            ) {
                Text("Save Auto Delete", color = Color.White)
            }
        }

        SectionCard {
            Text("Data Management", color = C.Text1, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { importContacts(ctx) }
                    .background(C.BlueDim)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Contacts, contentDescription = null, tint = C.Blue)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Import Phone Contacts", color = C.Text1, fontWeight = FontWeight.SemiBold)
                    Text("Add valid numbers from saved contacts", color = C.Text2, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { clr = true }
                    .background(C.RedDim)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = C.Red)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clear All Data", color = C.Text1, fontWeight = FontWeight.SemiBold)
                    Text("Remove stored numbers, logs and settings", color = C.Text2, fontSize = 12.sp)
                }
            }
        }

        SectionCard {
            Text("Stored Totals", color = C.Text1, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            StatRow("Total saved", "${ContactManager.getLifetimeTotal(ctx)}")
            Divider(color = C.Border, modifier = Modifier.padding(vertical = 8.dp))
            StatRow("Successful installs", "${ContactManager.getLifetimeSuccess(ctx)}")
            Divider(color = C.Border, modifier = Modifier.padding(vertical = 8.dp))
            StatRow("Generated numbers", "${ContactManager.getGeneratedCount(ctx)}")
            Divider(color = C.Border, modifier = Modifier.padding(vertical = 8.dp))
            StatRow("Pending numbers", "${ContactManager.getPendingCount(ctx)}")
            Divider(color = C.Border, modifier = Modifier.padding(vertical = 8.dp))
            StatRow("Installed numbers", "${ContactManager.getInstalledCount(ctx)}")
        }

        Text(
            text = "One Nation v1.0.12",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = C.Text3,
            fontSize = 12.sp,
        )
    }

    if (clr) {
        AlertDialog(
            onDismissRequest = { clr = false },
            containerColor = C.Card,
            title = { Text("Clear all stored data?", color = C.Text1) },
            text = { Text("This clears numbers, logs and app settings.", color = C.Text2) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ctx.getSharedPreferences(DATA_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
                        ctx.getSharedPreferences(LOGS_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
                        ctx.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
                        dailyTargetInput = DEFAULT_DAILY_TARGET.toString()
                        autoDeleteSelection = "never"
                        simId = SimSelection.AUTO_SUBSCRIPTION_ID
                        simOptions = SimSelection.getAvailableSimOptions(ctx)
                        clr = false
                        Toast.makeText(ctx, "All data cleared", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Clear", color = C.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { clr = false }) {
                    Text("Cancel", color = C.Text2)
                }
            },
        )
    }
}

@Composable
fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = C.Text3, fontSize = 12.sp)
        Text(value, color = C.Text2, fontSize = 12.sp, textAlign = TextAlign.End)
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = C.Text2, fontSize = 14.sp)
        Text(value, color = C.Text1, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Emp(icon: ImageVector, title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = C.Text3, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = C.Text2, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = C.Text3, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

fun importContacts(ctx: Context) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(ctx, "Contact permission is required", Toast.LENGTH_SHORT).show()
        return
    }

    var count = 0
    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    val cursor: Cursor? = ctx.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        null,
        null,
        null,
        null,
    )

    cursor?.use {
        val numberColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (it.moveToNext()) {
            val cleaned = it.getString(numberColumn)
                .replace(Regex("[^0-9+]"), "")
                .replace("+254", "0")
                .trim()

            if (cleaned.matches(Regex("^0[17]\\d{8}$")) &&
                !ContactManager.isPending(ctx, cleaned) &&
                !ContactManager.isInstalled(ctx, cleaned)
            ) {
                ContactManager.saveNumber(
                    ctx,
                    SavedNumber(
                        phone = cleaned,
                        dateAdded = date,
                        timeAdded = time,
                        status = "IMPORTED_PENDING",
                        source = "IMPORTED_CONTACTS",
                    ),
                )
                count++
            }
        }
    }

    Toast.makeText(ctx, "Imported $count numbers", Toast.LENGTH_SHORT).show()
}

fun vibrate(ctx: Context) {
    try {
        val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    } catch (_: Exception) {
    }
}

fun startRecommendationService(ctx: Context) {
    val intent = Intent(ctx, RecommendationService::class.java)
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(ctx, intent)
        } else {
            ctx.startService(intent)
        }
    } catch (e: Exception) {
        Toast.makeText(ctx, e.message ?: "Unable to start service", Toast.LENGTH_LONG).show()
        saveLog(ctx, "[START ERROR] ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
    }
}

fun saveLog(ctx: Context, entry: String) {
    val prefs = ctx.getSharedPreferences(LOGS_PREFS, Context.MODE_PRIVATE)
    val logs = JSONArray(prefs.getString("logs", "[]") ?: "[]")
    logs.put(entry)
    while (logs.length() > 500) {
        logs.remove(0)
    }
    prefs.edit().putString("logs", logs.toString()).apply()
}

fun loadLogs(ctx: Context): List<String> {
    val logs = JSONArray(
        ctx.getSharedPreferences(LOGS_PREFS, Context.MODE_PRIVATE)
            .getString("logs", "[]") ?: "[]",
    )
    val result = mutableListOf<String>()
    for (index in 0 until logs.length()) {
        result.add(logs.getString(index))
    }
    return result.reversed()
}
