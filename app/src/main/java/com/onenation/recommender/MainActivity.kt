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
import androidx.compose.foundation.layout.weight
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
        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
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
                modifier = Modifier.height(64.dp),
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
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("One Nation", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.Text1)
                Text("Safaricom App Recommendations", fontSize = 13.sp, color = C.Text2)
            }
            if (run) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(C.GreenDim)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(C.Green),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE", color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "CURRENT SESSION",
            color = C.Text3,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatCard("Attempts", "$ta", C.Blue, Modifier.weight(1f))
            StatCard("Success", "$sc", C.Green, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatCard("Failed", "$fc", C.Red, Modifier.weight(1f))
            StatCard("Installed", "$ic", C.Orange, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "LIFETIME STATS",
            color = C.Text3,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatCard("Total", "$lt", C.Purple, Modifier.weight(1f))
            StatCard("OK", "$ls", C.Cyan, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatCard("Generated", "$gc", C.Blue, Modifier.weight(1f))
            StatCard("Pending", "$pc", C.Orange, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (run) {
                    ctx.startService(
                        Intent(ctx, RecommendationService::class.java).apply {
                            action = RecommendationService.ACT_STOP
                        },
                    )
                } else {
                    ctx.startService(Intent(ctx, RecommendationService::class.java))
                }
                vibrate(ctx)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (run) C.Red else C.Green),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                imageVector = if (run) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (run) "STOP" else "START",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { importContacts(ctx) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Blue),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Outlined.Contacts, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Import Phone Contacts", fontSize = 14.sp)
        }

        if (run) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.GreenDim)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(C.Green),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Running on ${SimSelection.getSelectedSimLabel(ctx)} - every 8 seconds",
                    color = C.Green,
                    fontSize = 12.sp,
                )
            }
        }

        if (ll.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = C.Card),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(12.dp)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(C.Green),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(ll, fontSize = 12.sp, color = C.Text2)
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = C.Card),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = C.Text3, letterSpacing = 1.sp)
        }
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Saved Numbers", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Text1)
        Text("${nums.size} pending", fontSize = 13.sp, color = C.Text2)
        Spacer(Modifier.height(16.dp))

        if (nums.isEmpty()) {
            Emp(Icons.Outlined.Group, "No saved", "Appear here for 23h retry")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Installed", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Text1)
                Text("${nums.size} confirmed", fontSize = 13.sp, color = C.Green)
            }
            if (nums.isNotEmpty()) {
                TextButton(onClick = { clr = true }) {
                    Text("Clear All", color = C.Red, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (nums.isEmpty()) {
            Emp(Icons.Outlined.CheckCircle, "No installs", "Confirmed installs appear here")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Activity Logs", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Text1)
        Text("${filteredLogs.size} entries", fontSize = 13.sp, color = C.Text2)
        Spacer(Modifier.height(12.dp))

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

        Spacer(Modifier.height(12.dp))
        if (filteredLogs.isEmpty()) {
            Emp(Icons.Outlined.ListAlt, "No logs", "Activity will appear here")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filteredLogs) { log ->
                    val logColor = when {
                        log.contains("SUCCESS") -> C.Green
                        log.contains("INSTALLED") -> C.Orange
                        log.contains("ERROR") -> C.Red
                        log.contains("FAILED") -> C.Blue
                        else -> C.Text2
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = C.Card),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                Modifier
                                    .padding(top = 4.dp)
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(logColor),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(log, fontSize = 11.sp, color = logColor)
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
    val prefs = ctx.getSharedPreferences("onenation_settings", Context.MODE_PRIVATE)
    var dt by remember { mutableStateOf(prefs.getInt("daily_target", 100).toString()) }
    var ad by remember { mutableStateOf(ContactManager.getAutoDeleteSetting(ctx)) }
    var clr by remember { mutableStateOf(false) }
    var simOptions by remember { mutableStateOf(SimSelection.getAvailableSimOptions(ctx)) }
    var simId by remember { mutableIntStateOf(SimSelection.getStoredSubscriptionId(ctx)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Text1)
        Text("Configure app", fontSize = 13.sp, color = C.Text2)
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = C.Card),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "DAILY TARGET",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = C.Text3,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Max per day", color = C.Text1, fontSize = 14.sp)
                        Text("Stops when reached", color = C.Text3, fontSize = 11.sp)
                    }
                    OutlinedTextField(
                        value = dt,
                        onValueChange = {
                            if (it.all { ch -> ch.isDigit() }) {
                                dt = it
                                prefs.edit().putInt("daily_target", it.toIntOrNull() ?: 100).apply()
                            }
                        },
                        modifier = Modifier.width(80.dp),
                        textStyle = TextStyle(
                            color = C.Text1,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.Green,
                            unfocusedBorderColor = Color(0xFF1E293B),
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = C.Card),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "AUTOMATION SIM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = C.Text3,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Choose which SIM slot sends the automation USSD request",
                    color = C.Text1,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text("Current: ${SimSelection.getSelectedSimLabel(ctx)}", color = C.Text3, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = simId == SimSelection.AUTO_SUBSCRIPTION_ID,
                        onClick = {
                            simId = SimSelection.AUTO_SUBSCRIPTION_ID
                            SimSelection.saveSelectedSubscriptionId(ctx, simId)
                        },
                        label = { Text("Auto", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = C.GreenDim,
                            selectedLabelColor = C.Green,
                        ),
                    )
                    simOptions.forEach { option ->
                        FilterChip(
                            selected = simId == option.subscriptionId,
                            onClick = {
                                simId = option.subscriptionId
                                SimSelection.saveSelectedSubscriptionId(ctx, simId)
                            },
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
                        "No active SIM detected. Auto uses the phone default telephony line.",
                        color = C.Text3,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = C.Card),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "AUTO-DELETE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = C.Text3,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("never", "daily", "weekly", "monthly").forEach { option ->
                        FilterChip(
                            selected = ad == option,
                            onClick = {
                                ad = option
                                ContactManager.setAutoDeleteSetting(ctx, option)
                            },
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
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = C.Card),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "DATA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = C.Text3,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { importContacts(ctx) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Contacts, null, tint = C.Blue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Import Contacts", color = C.Text1, fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
                Divider(color = Color(0xFF1E293B))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { clr = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.DeleteForever, null, tint = C.Red, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Clear All Data", color = C.Text1, fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = C.Card),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "STATS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = C.Text3,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SR("Total", "${ContactManager.getLifetimeTotal(ctx)}")
                    SR("Success", "${ContactManager.getLifetimeSuccess(ctx)}")
                    SR("Generated", "${ContactManager.getGeneratedCount(ctx)}")
                    SR("Pending", "${ContactManager.getPendingCount(ctx)}")
                    SR("Installed", "${ContactManager.getInstalledCount(ctx)}")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "One Nation v1.0.3",
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
                        dt = "100"
                        ad = "never"
                        simId = SimSelection.AUTO_SUBSCRIPTION_ID
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
