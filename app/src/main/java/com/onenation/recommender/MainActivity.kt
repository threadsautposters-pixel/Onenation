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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
    val Bg = Color(0xFF0A0E17)
    val Surface = Color(0xFF111827)
    val Card = Color(0xFF1A2332)
    val Green = Color(0xFF10B981)
    val GreenDim = Color(0xFF10B981).copy(alpha = 0.15f)
    val Blue = Color(0xFF3B82F6)
    val Orange = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
    val Purple = Color(0xFF8B5CF6)
    val Cyan = Color(0xFF06B6D4)
    val Text1 = Color(0xFFF1F5F9)
    val Text2 = Color(0xFF94A3B8)
    val Text3 = Color(0xFF64748B)
}

private const val DEFAULT_DAILY_TARGET = 1000
private const val APP_VERSION = "1.0.9"

class MainActivity : ComponentActivity() {
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (!granted.values.all { it }) {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
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
                modifier = Modifier.height(72.dp),
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
                                fontWeight = if (selectedTab == index) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
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
            Modifier
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
    val prefs = ctx.getSharedPreferences("onenation_settings", Context.MODE_PRIVATE)
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
    var dailyTarget by remember { mutableIntStateOf(prefs.getInt("daily_target", DEFAULT_DAILY_TARGET)) }

    DisposableEffect(Unit) {
        RecommendationService.onUpdate = {
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
            dailyTarget = prefs.getInt("daily_target", DEFAULT_DAILY_TARGET)
        }
        onDispose { RecommendationService.onUpdate = null }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            if (!run) {
                run = RecommendationService.isRunning
                pc = ContactManager.getPendingCount(ctx)
                si = ContactManager.getInstalledCount(ctx)
                lt = ContactManager.getLifetimeTotal(ctx)
                ls = ContactManager.getLifetimeSuccess(ctx)
                gc = ContactManager.getGeneratedCount(ctx)
                dailyTarget = prefs.getInt("daily_target", DEFAULT_DAILY_TARGET)
            }
        }
    }

    val successRate = if (ta == 0) "0%" else "${(sc * 100) / ta}%"
    val activeSimLabel = SimSelection.getSelectedSimLabel(ctx)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Operations Dashboard",
            subtitle = "Professional control panel for recommendations, imports and live activity",
            trailing = {
                StatusPill(
                    text = if (run) "Live" else "Ready",
                    color = if (run) C.Green else C.Blue,
                )
            },
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = C.Card),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Command Center", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Text1)
                Text(
                    if (run) {
                        "Automation is active, processing the queue, and continues running in the background."
                    } else {
                        "Everything is ready. Start the engine when you want to begin."
                    },
                    fontSize = 13.sp,
                    color = C.Text2,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill(text = activeSimLabel, color = C.Blue)
                    StatusPill(text = "Target $dailyTarget / day", color = C.Purple)
                    StatusPill(text = "$pc pending", color = C.Orange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
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
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (run) C.Red else C.Green),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            imageVector = if (run) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (run) "Stop Automation" else "Start Automation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                        )
                    }
                    OutlinedButton(
                        onClick = { importContacts(ctx) },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Text1),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.Contacts, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import Contacts", fontSize = 14.sp)
                    }
                }
                if (ll.isNotBlank()) {
                    Divider(color = Color(0xFF243041))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Latest Activity", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Text3)
                        Text(ll, fontSize = 14.sp, color = C.Text1)
                    }
                }
            }
        }

        SectionTitle(
            eyebrow = "Current Session",
            title = "Live campaign performance",
            subtitle = "Quick snapshot of this active run",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatCard("Attempts", "$ta", C.Blue, Modifier.weight(1f), "Processed this session")
            StatCard("Success", "$sc", C.Green, Modifier.weight(1f), "Conversion $successRate")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatCard("Failed", "$fc", C.Red, Modifier.weight(1f), "Needs review")
            StatCard("Installed", "$ic", C.Orange, Modifier.weight(1f), "Confirmed this run")
        }

        SectionTitle(
            eyebrow = "Lifetime Stats",
            title = "Overall performance",
            subtitle = "Long-term totals stored on this device",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatCard("Total", "$lt", C.Purple, Modifier.weight(1f), "All generated records")
            StatCard("Success", "$ls", C.Cyan, Modifier.weight(1f), "Confirmed installs")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatCard("Generated", "$gc", C.Blue, Modifier.weight(1f), "Unique number attempts")
            StatCard("Pending", "$pc", C.Orange, Modifier.weight(1f), "Installed records $si")
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    note: String = "",
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = C.Card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(label.uppercase(), fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
            }
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.Text1)
            if (note.isNotBlank()) {
                Text(note, fontSize = 11.sp, color = C.Text3, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.Text1)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = C.Text2, lineHeight = 18.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SectionTitle(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            eyebrow.uppercase(),
            color = C.Text3,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
        )
        Text(title, color = C.Text1, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = C.Text2, fontSize = 12.sp)
    }
}

@Composable
fun StatusPill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Saved Numbers",
            subtitle = "Pending recommendations waiting for retry or follow-up",
            trailing = { StatusPill(text = "${nums.size} pending", color = C.Orange) },
        )

        if (nums.isEmpty()) {
            Emp(Icons.Outlined.Group, "No saved", "Appear here for 23h retry")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(nums, key = { it.phone }) { n ->
                    val sourceColor = when {
                        n.source.contains("MPESA") -> C.Green
                        n.source.contains("CONTACTS") -> C.Orange
                        else -> C.Blue
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = C.Card),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    n.phone,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = C.Text1,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Added: ${n.dateAdded} ${n.timeAdded}",
                                        fontSize = 11.sp,
                                        color = C.Text3,
                                    )
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(sourceColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            n.source.replace("_", " "),
                                            fontSize = 10.sp,
                                            color = sourceColor,
                                        )
                                    }
                                }
                                if (n.lastAttempted.isNotBlank()) {
                                    Text("Last: ${n.lastAttempted}", fontSize = 10.sp, color = C.Text3)
                                }
                                if (n.nextRetryTime.isNotBlank()) {
                                    Text("Next: ${n.nextRetryTime}", fontSize = 10.sp, color = C.Orange)
                                }
                                if (n.retryCount > 0) {
                                    Text("Retries: ${n.retryCount}", fontSize = 10.sp, color = C.Text3)
                                }
                            }
                            IconButton(onClick = { del = n.phone }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    "Remove",
                                    tint = C.Text3,
                                    modifier = Modifier.size(20.dp),
                                )
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
            title = { Text("Remove?", color = C.Text1) },
            text = { Text("Remove $phone?", color = C.Text2) },
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
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Installed", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Text1)
                Spacer(Modifier.height(4.dp))
                Text("Confirmed installs recorded on this device", fontSize = 13.sp, color = C.Text2)
                Spacer(Modifier.height(10.dp))
                StatusPill(text = "${nums.size} confirmed", color = C.Green)
            }
            if (nums.isNotEmpty()) {
                TextButton(onClick = { clr = true }) {
                    Text("Clear All", color = C.Red, fontSize = 13.sp)
                }
            }
        }

        if (nums.isEmpty()) {
            Emp(Icons.Outlined.CheckCircle, "No installs", "Confirmed installs appear here")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(nums, key = { it.phone }) { n ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = C.GreenDim),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                null,
                                tint = C.Green,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    n.phone,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = C.Text1,
                                )
                                Text(
                                    "Added: ${n.dateAdded} ${n.timeAdded}",
                                    fontSize = 11.sp,
                                    color = C.Text3,
                                )
                                Text(n.source.replace("_", " "), fontSize = 11.sp, color = C.Green)
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
            title = { Text("Clear All?", color = C.Text1) },
            text = { Text("Remove ${nums.size} installed?", color = C.Text2) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ContactManager.deleteInstalledNumbers(ctx)
                        nums = emptyList()
                        clr = false
                    },
                ) {
                    Text("Clear All", color = C.Red)
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

    val filteredLogs = if (filter == "ALL") {
        logs
    } else {
        logs.filter { it.contains(filter) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Activity Logs",
            subtitle = "Structured results, labelled events, and final USSD responses for each run",
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = C.Card),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Log Overview", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Text1)
                Text(
                    "Review saved outcomes and inspect the final response returned by each USSD request.",
                    fontSize = 13.sp,
                    color = C.Text2,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill(text = "${filteredLogs.size} visible", color = C.Blue)
                    StatusPill(text = "${logs.size} total", color = C.Purple)
                    StatusPill(
                        text = if (RecommendationService.isRunning) "Background active" else "Background idle",
                        color = if (RecommendationService.isRunning) C.Green else C.Orange,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("ALL", "SUCCESS", "INSTALLED", "FAILED", "ERROR").forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (option) {
                            "SUCCESS" -> C.GreenDim
                            "INSTALLED" -> C.Orange.copy(alpha = 0.15f)
                            "FAILED" -> C.Blue.copy(alpha = 0.15f)
                            "ERROR" -> C.Red.copy(alpha = 0.15f)
                            else -> Color.White.copy(alpha = 0.05f)
                        },
                        selectedLabelColor = when (option) {
                            "SUCCESS" -> C.Green
                            "INSTALLED" -> C.Orange
                            "FAILED" -> C.Blue
                            "ERROR" -> C.Red
                            else -> C.Text2
                        },
                    ),
                )
            }
        }

        if (filteredLogs.isEmpty()) {
            Emp(Icons.Outlined.ListAlt, "No logs", "Activity will appear here")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredLogs) { log ->
                    LogEntryCard(log)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("onenation_settings", Context.MODE_PRIVATE)
    val storedDailyTarget = prefs.getInt("daily_target", DEFAULT_DAILY_TARGET)
    val storedAutoDelete = ContactManager.getAutoDeleteSetting(ctx)
    val storedSimId = SimSelection.getStoredSubscriptionId(ctx)
    var dt by remember { mutableStateOf(storedDailyTarget.toString()) }
    var ad by remember { mutableStateOf(storedAutoDelete) }
    var clr by remember { mutableStateOf(false) }
    var simOptions by remember { mutableStateOf(SimSelection.getAvailableSimOptions(ctx)) }
    var simId by remember { mutableIntStateOf(storedSimId) }
    var savedDt by remember { mutableIntStateOf(storedDailyTarget) }
    var savedAd by remember { mutableStateOf(storedAutoDelete) }
    var savedSimId by remember { mutableIntStateOf(storedSimId) }
    val parsedTarget = dt.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_DAILY_TARGET
    val isTargetDirty = dt != savedDt.toString()
    val isSimDirty = simId != savedSimId
    val isAutoDeleteDirty = ad != savedAd

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Refined controls with clear save actions for every important preference",
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = C.Card),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Configuration Summary", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Text1)
                Text("Review your active defaults before making changes.", fontSize = 13.sp, color = C.Text2)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill(text = "Target $savedDt / day", color = C.Purple)
                    StatusPill(text = SimSelection.getSelectedSimLabel(ctx), color = C.Blue)
                    StatusPill(text = savedAd.replaceFirstChar { it.uppercase() }, color = C.Green)
                }
            }
        }

        SettingsSectionCard(
            eyebrow = "Daily Target",
            title = "Set the daily recommendation limit",
            subtitle = "Default target is 1000 and applies whenever there is no saved preference.",
        ) {
            OutlinedTextField(
                value = dt,
                onValueChange = {
                    if (it.all { ch -> ch.isDigit() }) {
                        dt = it
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = C.Text1,
                    textAlign = TextAlign.Start,
                    fontSize = 16.sp,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                label = { Text("Daily target", color = C.Text3) },
                supportingText = {
                    Text("Recommended for high-volume runs: 1000", color = C.Text3)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.Green,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedTextColor = C.Text1,
                    unfocusedTextColor = C.Text1,
                ),
            )
            Button(
                onClick = {
                    prefs.edit().putInt("daily_target", parsedTarget).apply()
                    dt = parsedTarget.toString()
                    savedDt = parsedTarget
                    Toast.makeText(ctx, "Daily target saved", Toast.LENGTH_SHORT).show()
                },
                enabled = isTargetDirty,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Green),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Daily Target", fontWeight = FontWeight.SemiBold)
            }
        }

        SettingsSectionCard(
            eyebrow = "Automation SIM",
            title = "Choose the preferred SIM line",
            subtitle = "Pick a SIM, then save the selection explicitly.",
        ) {
            Text("Current saved line: ${SimSelection.getSelectedSimLabel(ctx)}", color = C.Text2, fontSize = 12.sp)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                Text(
                    "No active SIM detected. Auto will use the phone default telephony line.",
                    color = C.Text3,
                    fontSize = 11.sp,
                )
            }
            Button(
                onClick = {
                    SimSelection.saveSelectedSubscriptionId(ctx, simId)
                    savedSimId = simId
                    simOptions = SimSelection.getAvailableSimOptions(ctx)
                    Toast.makeText(ctx, "SIM preference saved", Toast.LENGTH_SHORT).show()
                },
                enabled = isSimDirty,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Green),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save SIM Selection", fontWeight = FontWeight.SemiBold)
            }
        }

        SettingsSectionCard(
            eyebrow = "Auto-Delete",
            title = "Control cleanup timing",
            subtitle = "Choose when installed numbers should be cleared automatically.",
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("never", "daily", "weekly", "monthly").forEach { option ->
                    FilterChip(
                        selected = ad == option,
                        onClick = { ad = option },
                        label = {
                            Text(
                                option.replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = C.GreenDim,
                            selectedLabelColor = C.Green,
                        ),
                    )
                }
            }
            Button(
                onClick = {
                    ContactManager.setAutoDeleteSetting(ctx, ad)
                    savedAd = ad
                    Toast.makeText(ctx, "Auto-delete preference saved", Toast.LENGTH_SHORT).show()
                },
                enabled = isAutoDeleteDirty,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Green),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Auto-Delete", fontWeight = FontWeight.SemiBold)
            }
        }

        SettingsSectionCard(
            eyebrow = "Data Tools",
            title = "Manage imported records",
            subtitle = "Keep maintenance actions grouped and easy to find.",
        ) {
            OutlinedButton(
                onClick = { importContacts(ctx) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Text1),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Contacts, null, tint = C.Blue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import Contacts", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { clr = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Red),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.DeleteForever, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear All Data", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        SettingsSectionCard(
            eyebrow = "Stats",
            title = "Stored totals",
            subtitle = "A compact summary of everything saved on this device.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SR("Total", "${ContactManager.getLifetimeTotal(ctx)}")
                Divider(color = Color(0xFF1E293B))
                SR("Success", "${ContactManager.getLifetimeSuccess(ctx)}")
                Divider(color = Color(0xFF1E293B))
                SR("Generated", "${ContactManager.getGeneratedCount(ctx)}")
                Divider(color = Color(0xFF1E293B))
                SR("Pending", "${ContactManager.getPendingCount(ctx)}")
                Divider(color = Color(0xFF1E293B))
                SR("Installed", "${ContactManager.getInstalledCount(ctx)}")
            }
        }

        Text(
            "One Nation v$APP_VERSION",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = C.Text3,
        )
    }

    if (clr) {
        AlertDialog(
            onDismissRequest = { clr = false },
            containerColor = C.Card,
            title = { Text("Clear All?", color = C.Text1) },
            text = { Text("Delete everything?", color = C.Text2) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ctx.getSharedPreferences("onenation_data", Context.MODE_PRIVATE).edit().clear().apply()
                        ctx.getSharedPreferences("onenation_logs", Context.MODE_PRIVATE).edit().clear().apply()
                        ctx.getSharedPreferences("onenation_settings", Context.MODE_PRIVATE).edit().clear().apply()
                        dt = DEFAULT_DAILY_TARGET.toString()
                        ad = "never"
                        savedDt = DEFAULT_DAILY_TARGET
                        savedAd = "never"
                        simId = SimSelection.AUTO_SUBSCRIPTION_ID
                        savedSimId = SimSelection.AUTO_SUBSCRIPTION_ID
                        simOptions = SimSelection.getAvailableSimOptions(ctx)
                        clr = false
                        Toast.makeText(ctx, "Cleared", Toast.LENGTH_SHORT).show()
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
fun SettingsSectionCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = C.Card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                eyebrow.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = C.Text3,
                letterSpacing = 1.2.sp,
            )
            Text(title, color = C.Text1, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = C.Text2, fontSize = 12.sp, lineHeight = 18.sp)
            content()
        }
    }
}

@Composable
fun LogEntryCard(log: String) {
    val type = when {
        log.contains("SUCCESS") -> "SUCCESS"
        log.contains("INSTALLED") -> "INSTALLED"
        log.contains("FAILED") -> "FAILED"
        log.contains("ERROR") -> "ERROR"
        else -> "INFO"
    }
    val color = when (type) {
        "SUCCESS" -> C.Green
        "INSTALLED" -> C.Orange
        "FAILED" -> C.Blue
        "ERROR" -> C.Red
        else -> C.Text2
    }
    val responseMarker = "| FINAL RESPONSE:"
    val hasResponse = log.contains(responseMarker)
    val summary = if (hasResponse) log.substringBefore(responseMarker).trim() else log
    val response = if (hasResponse) log.substringAfter(responseMarker).trim() else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = C.Card),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(text = type, color = color)
                Text("Event Entry", color = C.Text3, fontSize = 11.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Summary", color = C.Text3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(summary, fontSize = 13.sp, color = C.Text1, lineHeight = 18.sp)
            }
            if (response.isNotBlank()) {
                Divider(color = Color(0xFF243041))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Final USSD Response", color = C.Text3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(response, fontSize = 12.sp, color = color, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
fun SR(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = C.Text2, fontSize = 14.sp)
        Text(value, color = C.Text1, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
            Icon(icon, null, tint = C.Text3, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = C.Text2, fontSize = 16.sp)
            Text(subtitle, color = C.Text3, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

fun importContacts(ctx: Context) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(ctx, "Permission required", Toast.LENGTH_SHORT).show()
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
    val requiredPermissions = listOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
    )
    val missingPermissions = requiredPermissions.filter {
        ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
    }
    if (missingPermissions.isNotEmpty()) {
        Toast.makeText(ctx, "Grant phone permissions before starting", Toast.LENGTH_LONG).show()
        saveLog(ctx, "[START BLOCKED] Missing permissions: ${missingPermissions.joinToString()}")
        return
    }

    val intent = Intent(ctx, RecommendationService::class.java)
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(ctx, intent)
        } else {
            ctx.startService(intent)
        }
    } catch (e: Throwable) {
        Toast.makeText(ctx, e.message ?: "Unable to start service", Toast.LENGTH_LONG).show()
        saveLog(ctx, "[START ERROR] ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
    }
}

fun saveLog(ctx: Context, entry: String) {
    val prefs = ctx.getSharedPreferences("onenation_logs", Context.MODE_PRIVATE)
    val logs = JSONArray(prefs.getString("logs", "[]") ?: "[]")
    logs.put(entry)
    while (logs.length() > 500) {
        logs.remove(0)
    }
    prefs.edit().putString("logs", logs.toString()).apply()
}

fun loadLogs(ctx: Context): List<String> {
    val logs = JSONArray(
        ctx.getSharedPreferences("onenation_logs", Context.MODE_PRIVATE)
            .getString("logs", "[]") ?: "[]",
    )
    val result = mutableListOf<String>()
    for (index in 0 until logs.length()) {
        result.add(logs.getString(index))
    }
    return result.reversed()
}
