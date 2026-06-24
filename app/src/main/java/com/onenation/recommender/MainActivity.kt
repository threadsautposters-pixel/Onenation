package com.onenation.recommender

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    Scaffold(containerColor = Color.Black) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            C.Blue.copy(alpha = 0.22f),
                            Color.Black,
                        ),
                        radius = 1200f,
                    ),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                colors = CardDefaults.cardColors(containerColor = C.Surface),
                shape = RoundedCornerShape(30.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AppTopTabs(
                        tabs = tabs.map { it.first },
                        selectedTab = selectedTab,
                        onSelect = { selectedTab = it },
                    )
                    Box(modifier = Modifier.weight(1f)) {
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
        }
    }
}

@Composable
fun AppTopTabs(
    tabs: List<String>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedTab == index
            val fg = if (selected) C.Green else C.Text2
            val bg = if (selected) C.Green.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label,
                    color = fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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
    var weeklyCommission by remember { mutableStateOf(CommissionManager.getWeeklyCommission(ctx)) }
    var monthlyCommission by remember { mutableStateOf(CommissionManager.getMonthlyCommission(ctx)) }
    var lifetimeCommission by remember { mutableStateOf(CommissionManager.getLifetimeCommission(ctx)) }
    var dailyTarget by remember { mutableIntStateOf(prefs.getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET)) }
    var executionIntervalLabel by remember { mutableStateOf(describeExecutionInterval(ctx)) }
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
        weeklyCommission = CommissionManager.getWeeklyCommission(ctx)
        monthlyCommission = CommissionManager.getMonthlyCommission(ctx)
        lifetimeCommission = CommissionManager.getLifetimeCommission(ctx)
        dailyTarget = prefs.getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET)
        executionIntervalLabel = describeExecutionInterval(ctx)
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
            subtitle = "Dispatch console for airtime & bundle automation",
            kicker = "AGENT 042  ·  ${SimSelection.getSelectedSimLabel(ctx)}",
        )

        HeroStatusCard(
            isRunning = run,
            simLabel = SimSelection.getSelectedSimLabel(ctx),
            dailyTarget = dailyTarget,
            executionIntervalLabel = executionIntervalLabel,
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
                MetricItem("Weekly Com.", formatCommission(weeklyCommission), C.Green),
                MetricItem("Monthly Com.", formatCommission(monthlyCommission), C.Blue),
                MetricItem("Lifetime Com.", formatCommission(lifetimeCommission), C.Purple),
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
            Spacer(Modifier.height(10.dp))
            Text(
                "Incoming calls pause automation until the line is idle, M-Pesa SMS pauses it temporarily, and commission SMS totals are stored across app restarts.",
                color = C.Text3,
                fontSize = 12.sp,
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
    executionIntervalLabel: String,
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
                        pauseRemainingMs > 0L -> "PAUSED"
                        callInProgress -> "ON CALL"
                        isRunning -> "TRANSMITTING"
                        else -> "IDLE"
                    },
                    color = when {
                        pauseRemainingMs > 0L -> C.Orange
                        callInProgress -> C.Orange
                        isRunning -> C.Green
                        else -> C.Red
                    },
                )
                StatusPill(label = simLabel, color = C.Orange)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    pauseRemainingMs > 0L ->
                        "Automation resumes in ${AutomationPauseManager.describeRemaining(pauseRemainingMs)}"
                    callInProgress -> "Automation paused while call is active"
                    isRunning -> "Automation is dialing in the background"
                    else -> "Automation is currently stopped"
                },
                color = C.Text1,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (pauseRemainingMs > 0L) {
                    "Automation is temporarily held to avoid interrupting active transactions."
                } else if (callInProgress) {
                    "Incoming or ongoing calls pause automation until the line is idle."
                } else {
                    "Ceiling set to $dailyTarget dials/day · one request goes out every $executionIntervalLabel."
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
                        Text(
                            "LAST TRANSMISSION",
                            color = C.Text3,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            lastLog,
                            color = C.Green,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
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
fun ScreenTitle(title: String, subtitle: String, kicker: String? = null) {
    Column {
        if (!kicker.isNullOrBlank()) {
            Text(
                kicker,
                fontSize = 10.sp,
                color = C.Text3,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
        }
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
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = C.Bg,
        ),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold)
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
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = color,
        ),
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
        ScreenTitle(
            title = "Saved Numbers",
            subtitle = "${nums.size} pending numbers waiting for follow-up",
            kicker = "QUEUE  ·  ${SimSelection.getSelectedSimLabel(ctx)}",
        )
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
            ScreenTitle(
                title = "Installed",
                subtitle = "${nums.size} confirmed installations",
                kicker = "CONFIRMED  ·  ${SimSelection.getSelectedSimLabel(ctx)}",
            )
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
        ScreenTitle(
            title = "Activity Logs",
            subtitle = "${filteredLogs.size} log entries",
            kicker = "MONITOR  ·  ${SimSelection.getSelectedSimLabel(ctx)}",
        )
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
    val appVersion = remember { appVersionName(ctx) }
    val scope = rememberCoroutineScope()
    var dailyTargetInput by remember {
        mutableStateOf(prefs.getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET).toString())
    }
    var executionIntervalInput by remember {
        mutableStateOf(getExecutionIntervalValue(ctx).toString())
    }
    var executionIntervalUnit by remember {
        mutableStateOf(getExecutionIntervalUnit(ctx))
    }
    var autoDeleteSelection by remember { mutableStateOf(ContactManager.getAutoDeleteSetting(ctx)) }
    var clr by remember { mutableStateOf(false) }
    var simOptions by remember { mutableStateOf(SimSelection.getAvailableSimOptions(ctx)) }
    var simId by remember { mutableIntStateOf(SimSelection.getStoredSubscriptionId(ctx)) }
    var restoreUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val downloadBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        BackupData.writeBackup(ctx, uri)
                    }
                }
                Toast.makeText(
                    ctx,
                    if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.message.orEmpty()}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    val restoreBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            restoreUris = uris
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(
            title = "Settings",
            subtitle = "Device configuration for automation",
            kicker = "CONFIG  ·  ${SimSelection.getSelectedSimLabel(ctx)}",
        )

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
            Text("Execution Interval", color = C.Text1, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose how often automation runs. The default is 8 seconds.",
                color = C.Text2,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            DetailLine("Current interval", describeExecutionInterval(ctx))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = executionIntervalInput,
                onValueChange = {
                    if (it.all(Char::isDigit)) {
                        executionIntervalInput = it
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Interval value", color = C.Text3) },
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
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    EXECUTION_INTERVAL_UNIT_SECONDS to "Seconds",
                    EXECUTION_INTERVAL_UNIT_MINUTES to "Minutes",
                ).forEach { (unit, label) ->
                    FilterChip(
                        selected = executionIntervalUnit == unit,
                        onClick = { executionIntervalUnit = unit },
                        label = { Text(label, fontSize = 12.sp) },
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
                    val value = executionIntervalInput.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_EXECUTION_INTERVAL_VALUE
                    prefs.edit()
                        .putInt(KEY_EXECUTION_INTERVAL_VALUE, value)
                        .putString(KEY_EXECUTION_INTERVAL_UNIT, executionIntervalUnit)
                        .apply()
                    executionIntervalInput = value.toString()
                    Toast.makeText(
                        ctx,
                        "Execution interval saved: ${formatExecutionInterval(value, executionIntervalUnit)}",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Green),
            ) {
                Text("Save Execution Interval", color = Color.White)
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
                    .clickable {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
                        downloadBackupLauncher.launch("OneNation-backup-$stamp.json")
                    }
                    .background(C.BlueDim)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = C.Blue)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download Backup Data", color = C.Text1, fontWeight = FontWeight.SemiBold)
                    Text("Save numbers, logs and settings to a file", color = C.Text2, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { restoreBackupLauncher.launch(arrayOf("application/json", "text/*")) }
                    .background(C.OrangeDim)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = C.Orange)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Restore Backup Data", color = C.Text1, fontWeight = FontWeight.SemiBold)
                    Text("Select one or more backups and merge them with current data", color = C.Text2, fontSize = 12.sp)
                }
            }
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
            Divider(color = C.Border, modifier = Modifier.padding(vertical = 8.dp))
            StatRow("Weekly commission", formatCommission(CommissionManager.getWeeklyCommission(ctx)))
            Divider(color = C.Border, modifier = Modifier.padding(vertical = 8.dp))
            StatRow("Monthly commission", formatCommission(CommissionManager.getMonthlyCommission(ctx)))
            Divider(color = C.Border, modifier = Modifier.padding(vertical = 8.dp))
            StatRow("Lifetime commission", formatCommission(CommissionManager.getLifetimeCommission(ctx)))
        }

        Text(
            text = "One Nation v$appVersion",
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
                        executionIntervalInput = DEFAULT_EXECUTION_INTERVAL_VALUE.toString()
                        executionIntervalUnit = EXECUTION_INTERVAL_UNIT_SECONDS
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

    if (restoreUris.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { restoreUris = emptyList() },
            containerColor = C.Card,
            title = { Text("Merge backup data?", color = C.Text1) },
            text = {
                Text(
                    "This will merge ${restoreUris.size} backup file(s) with your current numbers, logs and totals. The app settings will use the backup with the most data.",
                    color = C.Text2,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = restoreUris
                        restoreUris = emptyList()
                        if (selected.isEmpty()) return@TextButton
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    BackupData.restoreFromBackups(ctx, selected)
                                }
                            }
                            if (result.isSuccess) {
                                dailyTargetInput = prefs.getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET).toString()
                                executionIntervalInput = getExecutionIntervalValue(ctx).toString()
                                executionIntervalUnit = getExecutionIntervalUnit(ctx)
                                autoDeleteSelection = ContactManager.getAutoDeleteSetting(ctx)
                                simId = SimSelection.getStoredSubscriptionId(ctx)
                                simOptions = SimSelection.getAvailableSimOptions(ctx)
                            }
                            Toast.makeText(
                                ctx,
                                if (result.isSuccess) "Backup data merged" else "Restore failed: ${result.exceptionOrNull()?.message.orEmpty()}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ) {
                    Text("Restore", color = C.Orange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreUris = emptyList() }) {
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

fun appVersionName(ctx: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        }
        packageInfo.versionName.orEmpty().ifBlank { "unknown" }
    } catch (_: Exception) {
        "unknown"
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

fun formatCommission(value: Double): String {
    val whole = value.toLong().toDouble() == value
    return if (whole) {
        "Ksh ${value.toLong()}"
    } else {
        "Ksh %.2f".format(Locale.US, value)
    }
}
