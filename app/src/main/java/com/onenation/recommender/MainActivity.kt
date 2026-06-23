package com.onenation.recommender
import android.Manifest; import android.content.Context; import android.content.Intent; import android.content.pm.PackageManager; import android.database.Cursor; import android.os.Build; import android.os.Bundle; import android.os.VibrationEffect; import android.os.Vibrator; import android.provider.ContactsContract; import android.widget.Toast
import androidx.activity.ComponentActivity; import androidx.activity.compose.setContent; import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background; import androidx.compose.foundation.clickable; import androidx.compose.foundation.layout.*; import androidx.compose.foundation.lazy.LazyColumn; import androidx.compose.foundation.lazy.items; import androidx.compose.foundation.rememberScrollState; import androidx.compose.foundation.shape.RoundedCornerShape; import androidx.compose.foundation.text.KeyboardOptions; import androidx.compose.foundation.verticalScroll; import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.filled.*; import androidx.compose.material.icons.outlined.*; import androidx.compose.material3.*; import androidx.compose.runtime.*; import androidx.compose.ui.Alignment; import androidx.compose.ui.Modifier; import androidx.compose.ui.draw.clip; import androidx.compose.ui.graphics.Color; import androidx.compose.ui.graphics.vector.ImageVector; import androidx.compose.ui.platform.LocalContext; import androidx.compose.ui.text.font.FontWeight; import androidx.compose.ui.text.input.KeyboardType; import androidx.compose.ui.text.style.TextAlign; import androidx.compose.ui.unit.dp; import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat; import kotlinx.coroutines.delay; import java.text.SimpleDateFormat; import java.util.*
private object C { val Bg=Color(0xFF0A0E17); val Surface=Color(0xFF111827); val Card=Color(0xFF1A2332); val Green=Color(0xFF10B981); val GreenDim=Color(0xFF10B981).copy(alpha=0.15f); val Blue=Color(0xFF3B82F6); val Orange=Color(0xFFF59E0B); val Red=Color(0xFFEF4444); val Purple=Color(0xFF8B5CF6); val Cyan=Color(0xFF06B6D4); val Text1=Color(0xFFF1F5F9); val Text2=Color(0xFF94A3B8); val Text3=Color(0xFF64748B) }
class MainActivity : ComponentActivity() {
    private val rpl = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if(!it.values.all{it}) Toast.makeText(this,"Permissions required",Toast.LENGTH_LONG).show() }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); val needed=mutableListOf(Manifest.permission.CALL_PHONE,Manifest.permission.READ_CONTACTS,Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_SMS,Manifest.permission.READ_PHONE_STATE); if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) needed.add(Manifest.permission.POST_NOTIFICATIONS); val missing=needed.filter{ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED}; if(missing.isNotEmpty()) rpl.launch(missing.toTypedArray()); setContent{MaterialTheme(colorScheme=darkColorScheme(primary=C.Green,secondary=C.Blue,background=C.Bg,surface=C.Surface)){App()}} }
}
@Composable fun App() { var t by remember{mutableIntStateOf(0)}; val tabs=listOf("Dashboard" to Icons.Filled.Dashboard,"Saved" to Icons.Filled.Group,"Installed" to Icons.Filled.CheckCircle,"Logs" to Icons.Filled.ListAlt,"Settings" to Icons.Filled.Settings); Scaffold(containerColor=C.Bg,bottomBar={NavigationBar(containerColor=C.Surface,modifier=Modifier.height(64.dp)){tabs.forEachIndexed{i,(l,ic)->NavigationBarItem(selected=t==i,onClick={t=i},icon={Icon(ic,l,Modifier.size(22.dp))},label={Text(l,fontSize=11.sp,fontWeight=if(t==i)FontWeight.SemiBold else FontWeight.Normal)},colors=NavigationBarItemDefaults.colors(selectedIconColor=C.Green,selectedTextColor=C.Green,unselectedIconColor=C.Text3,unselectedTextColor=C.Text3,indicatorColor=C.GreenDim))}}}){p->Box(Modifier.padding(p).fillMaxSize()){when(t){0->Dash();1->Saved();2->Installed();3->Logs();4->Settings()}}}
@Composable fun Dash() { val ctx=LocalContext.current; var run by remember{mutableStateOf(RecommendationService.isRunning)}; var sc by remember{mutableIntStateOf(RecommendationService.successCount)}; var ta by remember{mutableIntStateOf(RecommendationService.totalAttempts)}; var fc by remember{mutableIntStateOf(RecommendationService.failedCount)}; var ic by remember{mutableIntStateOf(RecommendationService.installedCount)}; var ll by remember{mutableStateOf(RecommendationService.lastLog)}; var pc by remember{mutableIntStateOf(ContactManager.getPendingCount(ctx))}; var si by remember{mutableIntStateOf(ContactManager.getInstalledCount(ctx))}; var lt by remember{mutableLongStateOf(ContactManager.getLifetimeTotal(ctx))}; var ls by remember{mutableLongStateOf(ContactManager.getLifetimeSuccess(ctx))}; var gc by remember{mutableLongStateOf(ContactManager.getGeneratedCount(ctx))}
    DisposableEffect(Unit){RecommendationService.onUpdate={run=RecommendationService.isRunning;sc=RecommendationService.successCount;ta=RecommendationService.totalAttempts;fc=RecommendationService.failedCount;ic=RecommendationService.installedCount;ll=RecommendationService.lastLog;pc=ContactManager.getPendingCount(ctx);si=ContactManager.getInstalledCount(ctx);lt=ContactManager.getLifetimeTotal(ctx);ls=ContactManager.getLifetimeSuccess(ctx);gc=ContactManager.getGeneratedCount(ctx)};onDispose{RecommendationService.onUpdate=null}}
    LaunchedEffect(Unit){while(true){delay(2000);if(!run){run=RecommendationService.isRunning;pc=ContactManager.getPendingCount(ctx);si=ContactManager.getInstalledCount(ctx);lt=ContactManager.getLifetimeTotal(ctx);ls=ContactManager.getLifetimeSuccess(ctx);gc=ContactManager.getGeneratedCount(ctx)}}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("One Nation",fontSize=28.sp,fontWeight=FontWeight.Bold,color=C.Text1);Text("Safaricom App Recommendations",fontSize=13.sp,color=C.Text2)};if(run)Box(Modifier.clip(RoundedCornerShape(50)).background(C.GreenDim).padding(horizontal=12.dp,vertical=6.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(C.Green));Spacer(Modifier.width(6.dp));Text("LIVE",color=C.Green,fontSize=12.sp,fontWeight=FontWeight.Bold)}}}
        Spacer(Modifier.height(20.dp))
        Text("CURRENT SESSION",color=C.Text3,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.5.sp);Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){StatCard("Attempts","$ta",C.Blue,Modifier.weight(1f));StatCard("Success","$sc",C.Green,Modifier.weight(1f))}
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){StatCard("Failed","$fc",C.Red,Modifier.weight(1f));StatCard("Installed","$ic",C.Orange,Modifier.weight(1f))}
        Spacer(Modifier.height(20.dp))
        Text("LIFETIME STATS",color=C.Text3,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.5.sp);Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){StatCard("Total","$lt",C.Purple,Modifier.weight(1f));StatCard("OK","$ls",C.Cyan,Modifier.weight(1f))}
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){StatCard("Generated","$gc",C.Blue,Modifier.weight(1f));StatCard("Pending","$pc",C.Orange,Modifier.weight(1f))}
        Spacer(Modifier.height(20.dp))
        Button(onClick={if(run)ctx.startService(Intent(ctx,RecommendationService::class.java).apply{action=RecommendationService.ACT_STOP})else ctx.startService(Intent(ctx,RecommendationService::class.java));vibrate(ctx)},modifier=Modifier.fillMaxWidth().height(58.dp),colors=ButtonDefaults.buttonColors(containerColor=if(run)C.Red else C.Green),shape=RoundedCornerShape(16.dp)){Icon(if(run)Icons.Filled.Stop else Icons.Filled.PlayArrow,null,tint=Color.White,modifier=Modifier.size(24.dp));Spacer(Modifier.width(10.dp));Text(if(run)"STOP" else "START",fontWeight=FontWeight.Bold,fontSize=15.sp,color=Color.White)}
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick={importContacts(ctx)},modifier=Modifier.fillMaxWidth().height(48.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=C.Blue),shape=RoundedCornerShape(12.dp)){Icon(Icons.Outlined.Contacts,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Import Phone Contacts",fontSize=14.sp)}
        if(run){Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.GreenDim).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(C.Green));Spacer(Modifier.width(10.dp));Text("Running on ${SimSelection.getSelectedSimLabel(ctx)} - every 8 seconds",color=C.Green,fontSize=12.sp)}}
        if(ll.isNotBlank()){Spacer(Modifier.height(12.dp));Card(colors=CardDefaults.cardColors(containerColor=C.Card),shape=RoundedCornerShape(12.dp)){Row(Modifier.padding(12.dp)){Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(C.Green));Spacer(Modifier.width(10.dp));Text(ll,fontSize=12.sp,color=C.Text2)}}}
    }
}
@Composable fun StatCard(l: String, v: String, c: Color, m: Modifier=Modifier) { Card(m,colors=CardDefaults.cardColors(containerColor=C.Card),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(v,fontSize=26.sp,fontWeight=FontWeight.Bold,color=c);Spacer(Modifier.height(4.dp));Text(l,fontSize=11.sp,color=C.Text3,letterSpacing=1.sp)}} }
@Composable fun Saved() { val ctx=LocalContext.current; var nums by remember{mutableStateOf(ContactManager.getPendingNumbers(ctx))}; var del by remember{mutableStateOf<String?>(null)}; LaunchedEffect(Unit){while(true){delay(2000);nums=ContactManager.getPendingNumbers(ctx)}}; Column(Modifier.fillMaxSize().padding(16.dp)){Text("Saved Numbers",fontSize=22.sp,fontWeight=FontWeight.Bold,color=C.Text1);Text("${nums.size} pending",fontSize=13.sp,color=C.Text2);Spacer(Modifier.height(16.dp));if(nums.isEmpty())Emp(Icons.Outlined.Group,"No saved","Appear here for 23h retry") else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(nums,key={it.phone}){n->val sc=when{n.source.contains("MPESA")->C.Green;n.source.contains("CONTACTS")->C.Orange;else->C.Blue};Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=C.Card),shape=RoundedCornerShape(12.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(n.phone,fontSize=16.sp,fontWeight=FontWeight.Medium,color=C.Text1);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Text("Added: ${n.dateAdded} ${n.timeAdded}",fontSize=11.sp,color=C.Text3);Box(Modifier.clip(RoundedCornerShape(50)).background(sc.copy(alpha=0.15f)).padding(horizontal=8.dp,vertical=2.dp)){Text(n.source.replace("_"," "),fontSize=10.sp,color=sc)}};if(n.lastAttempted.isNotBlank())Text("Last: ${n.lastAttempted}",fontSize=10.sp,color=C.Text3);if(n.nextRetryTime.isNotBlank())Text("Next: ${n.nextRetryTime}",fontSize=10.sp,color=C.Orange);if(n.retryCount>0)Text("Retries: ${n.retryCount}",fontSize=10.sp,color=C.Text3)};IconButton(onClick={del=n.phone}){Icon(Icons.Outlined.Delete,"Remove",tint=C.Text3,modifier=Modifier.size(20.dp))}}}};item{Spacer(Modifier.height(8.dp))}}};del?.let{ph->AlertDialog(onDismissRequest={del=null},containerColor=C.Card,title={Text("Remove?",color=C.Text1)},text={Text("Remove $ph?",color=C.Text2)},confirmButton={TextButton(onClick={ContactManager.deleteNumber(ctx,ph);nums=ContactManager.getPendingNumbers(ctx);del=null}){Text("Remove",color=C.Red)}},dismissButton={TextButton(onClick={del=null}){Text("Cancel",color=C.Text2)}})}}
@Composable fun Installed() { val ctx=LocalContext.current; var nums by remember{mutableStateOf(ContactManager.getInstalledNumbers(ctx))}; var clr by remember{mutableStateOf(false)}; LaunchedEffect(Unit){while(true){delay(2000);nums=ContactManager.getInstalledNumbers(ctx)}}; Column(Modifier.fillMaxSize().padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("Installed",fontSize=22.sp,fontWeight=FontWeight.Bold,color=C.Text1);Text("${nums.size} confirmed",fontSize=13.sp,color=C.Green)};if(nums.isNotEmpty())TextButton(onClick={clr=true}){Text("Clear All",color=C.Red,fontSize=13.sp)}};Spacer(Modifier.height(16.dp));if(nums.isEmpty())Emp(Icons.Outlined.CheckCircle,"No installs","Confirmed installs appear here") else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(nums,key={it.phone}){n->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=C.GreenDim),shape=RoundedCornerShape(12.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Filled.CheckCircle,null,tint=C.Green,modifier=Modifier.size(24.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(n.phone,fontSize=16.sp,fontWeight=FontWeight.Medium,color=C.Text1);Text("Added: ${n.dateAdded} ${n.timeAdded}",fontSize=11.sp,color=C.Text3);Text(n.source.replace("_"," "),fontSize=11.sp,color=C.Green)}}}};item{Spacer(Modifier.height(8.dp))}}};if(clr)AlertDialog(onDismissRequest={clr=false},containerColor=C.Card,title={Text("Clear All?",color=C.Text1)},text={Text("Remove ${nums.size} installed?",color=C.Text2)},confirmButton={TextButton(onClick={ContactManager.deleteInstalledNumbers(ctx);nums=emptyList();clr=false}){Text("Clear All",color=C.Red)}},dismissButton={TextButton(onClick={clr=false}){Text("Cancel",color=C.Text2)}})}
@Composable fun Logs() { val ctx=LocalContext.current; var logs by remember{mutableStateOf(loadLogs(ctx))}; var f by remember{mutableStateOf("ALL")}; LaunchedEffect(Unit){while(true){delay(3000);logs=loadLogs(ctx)}}; val fl=if(f=="ALL")logs else logs.filter{it.contains(f)}; Column(Modifier.fillMaxSize().padding(16.dp)){Text("Activity Logs",fontSize=22.sp,fontWeight=FontWeight.Bold,color=C.Text1);Text("${fl.size} entries",fontSize=13.sp,color=C.Text2);Spacer(Modifier.height(12.dp));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("ALL","SUCCESS","INSTALLED","FAILED","ERROR").forEach{o->FilterChip(selected=f==o,onClick={f=o},label={Text(o,fontSize=12.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=when(o){"SUCCESS"->C.GreenDim;"INSTALLED"->C.Orange.copy(alpha=0.15f);"FAILED"->C.Blue.copy(alpha=0.15f);"ERROR"->C.Red.copy(alpha=0.15f);else->Color.White.copy(alpha=0.05f)},selectedLabelColor=when(o){"SUCCESS"->C.Green;"INSTALLED"->C.Orange;"FAILED"->C.Blue;"ERROR"->C.Red;else->C.Text2}))}};Spacer(Modifier.height(12.dp));if(fl.isEmpty())Emp(Icons.Outlined.ListAlt,"No logs","Activity will appear here") else LazyColumn(verticalArrangement=Arrangement.spacedBy(4.dp)){items(fl){l->val lc=when{l.contains("SUCCESS")->C.Green;l.contains("INSTALLED")->C.Orange;l.contains("ERROR")->C.Red;l.contains("FAILED")->C.Blue;else->C.Text2};Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=C.Card),shape=RoundedCornerShape(8.dp)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.Top){Box(Modifier.padding(top=4.dp).size(6.dp).clip(RoundedCornerShape(50)).background(lc));Spacer(Modifier.width(8.dp));Text(l,fontSize=11.sp,color=lc)}}}};item{Spacer(Modifier.height(8.dp))}}}
@Composable fun Settings() {
    val ctx=LocalContext.current
    val prefs=ctx.getSharedPreferences("onenation_settings",Context.MODE_PRIVATE)
    var dt by remember{mutableStateOf(prefs.getInt("daily_target",100).toString())}
    var ad by remember{mutableStateOf(ContactManager.getAutoDeleteSetting(ctx))}
    var clr by remember{mutableStateOf(false)}
    var simOptions by remember{mutableStateOf(SimSelection.getAvailableSimOptions(ctx))}
    var simId by remember{mutableIntStateOf(SimSelection.getStoredSubscriptionId(ctx))}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Text("Settings",fontSize=22.sp,fontWeight=FontWeight.Bold,color=C.Text1)
        Text("Configure app",fontSize=13.sp,color=C.Text2)
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=C.Card),shape=RoundedCornerShape(14.dp)){
            Column(Modifier.padding(16.dp)){
                Text("DAILY TARGET",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=C.Text3,letterSpacing=1.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                    Column{
                        Text("Max per day",color=C.Text1,fontSize=14.sp)
                        Text("Stops when reached",color=C.Text3,fontSize=11.sp)
                    }
                    OutlinedTextField(value=dt,onValueChange={if(it.all{c->c.isDigit()}){dt=it;prefs.edit().putInt("daily_target",it.toIntOrNull()?:100).apply()}},modifier=Modifier.width(80.dp),textStyle=androidx.compose.ui.text.TextStyle(color=C.Text1,textAlign=TextAlign.Center,fontSize=16.sp),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),shape=RoundedCornerShape(10.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=C.Green,unfocusedBorderColor=Color(0xFF1E293B)))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=C.Card),shape=RoundedCornerShape(14.dp)){
            Column(Modifier.padding(16.dp)){
                Text("AUTOMATION SIM",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=C.Text3,letterSpacing=1.sp)
                Spacer(Modifier.height(10.dp))
                Text("Choose which SIM slot sends the automation USSD request",color=C.Text1,fontSize=14.sp)
                Spacer(Modifier.height(4.dp))
                Text("Current: ${SimSelection.getSelectedSimLabel(ctx)}",color=C.Text3,fontSize=11.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    FilterChip(selected=simId==SimSelection.AUTO_SUBSCRIPTION_ID,onClick={simId=SimSelection.AUTO_SUBSCRIPTION_ID;SimSelection.saveSelectedSubscriptionId(ctx,simId)},label={Text("Auto",fontSize=12.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=C.GreenDim,selectedLabelColor=C.Green))
                    simOptions.forEach{o->
                        FilterChip(selected=simId==o.subscriptionId,onClick={simId=o.subscriptionId;SimSelection.saveSelectedSubscriptionId(ctx,simId)},label={Text(o.label,fontSize=12.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=C.GreenDim,selectedLabelColor=C.Green))
                    }
                }
                if(simOptions.isEmpty()){
                    Spacer(Modifier.height(10.dp))
                    Text("No active SIM detected. Auto uses the phone default telephony line.",color=C.Text3,fontSize=11.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=C.Card),shape=RoundedCornerShape(14.dp)){
            Column(Modifier.padding(16.dp)){
                Text("AUTO-DELETE",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=C.Text3,letterSpacing=1.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("never","daily","weekly","monthly").forEach{o->
                        FilterChip(selected=ad==o,onClick={ad=o;ContactManager.setAutoDeleteSetting(ctx,o)},label={Text(o.replaceFirstChar{it.uppercase()},fontSize=12.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=C.GreenDim,selectedLabelColor=C.Green))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=C.Card),shape=RoundedCornerShape(14.dp)){
            Column(Modifier.padding(16.dp)){
                Text("DATA",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=C.Text3,letterSpacing=1.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable{importContacts(ctx)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.Contacts,null,tint=C.Blue,modifier=Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Import Contacts",color=C.Text1,fontSize=14.sp,modifier=Modifier.weight(1f))
                }
                HorizontalDivider(color=Color(0xFF1E293B))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable{clr=true}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.DeleteForever,null,tint=C.Red,modifier=Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Clear All Data",color=C.Text1,fontSize=14.sp,modifier=Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=C.Card),shape=RoundedCornerShape(14.dp)){
            Column(Modifier.padding(16.dp)){
                Text("STATS",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=C.Text3,letterSpacing=1.sp)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    SR("Total","${ContactManager.getLifetimeTotal(ctx)}")
                    SR("Success","${ContactManager.getLifetimeSuccess(ctx)}")
                    SR("Generated","${ContactManager.getGeneratedCount(ctx)}")
                    SR("Pending","${ContactManager.getPendingCount(ctx)}")
                    SR("Installed","${ContactManager.getInstalledCount(ctx)}")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("One Nation v1.0.1",modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,fontSize=12.sp,color=C.Text3)
    }
    if(clr)AlertDialog(onDismissRequest={clr=false},containerColor=C.Card,title={Text("Clear All?",color=C.Text1)},text={Text("Delete everything?",color=C.Text2)},confirmButton={TextButton(onClick={ctx.getSharedPreferences("onenation_data",Context.MODE_PRIVATE).edit().clear().apply();ctx.getSharedPreferences("onenation_logs",Context.MODE_PRIVATE).edit().clear().apply();ctx.getSharedPreferences("onenation_settings",Context.MODE_PRIVATE).edit().clear().apply();dt="100";ad="never";simId=SimSelection.AUTO_SUBSCRIPTION_ID;simOptions=SimSelection.getAvailableSimOptions(ctx);clr=false;Toast.makeText(ctx,"Cleared",Toast.LENGTH_SHORT).show()}){Text("Clear",color=C.Red,fontWeight=FontWeight.Bold)}},dismissButton={TextButton(onClick={clr=false}){Text("Cancel",color=C.Text2)}})
}
@Composable fun SR(l: String, v: String) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(l,color=C.Text2,fontSize=14.sp);Text(v,color=C.Text1,fontSize=14.sp,fontWeight=FontWeight.Medium)} }
@Composable fun Emp(ic: ImageVector, t: String, s: String) { Box(Modifier.fillMaxWidth().padding(32.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(ic,null,tint=C.Text3,modifier=Modifier.size(48.dp));Spacer(Modifier.height(12.dp));Text(t,color=C.Text2,fontSize=16.sp);Text(s,color=C.Text3,fontSize=12.sp,textAlign=TextAlign.Center)}} }
fun importContacts(ctx: Context) { if(ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){Toast.makeText(ctx,"Permission required",Toast.LENGTH_SHORT).show();return};var c=0;val n=SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(Date());val t=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date());val cr: Cursor?=ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,null,null,null,null);cr?.use{val nc=it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);while(it.moveToNext()){val cl=it.getString(nc).replace(Regex("[^0-9+]"),"").replace("+254","0").trim();if(cl.matches(Regex("^0[17]\\d{8}$"))&&!ContactManager.isPending(ctx,cl)&&!ContactManager.isInstalled(ctx,cl)){ContactManager.saveNumber(ctx,SavedNumber(phone=cl,dateAdded=n,timeAdded=t,status="IMPORTED_PENDING",source="IMPORTED_CONTACTS"));c++}}};Toast.makeText(ctx,"Imported $c numbers",Toast.LENGTH_SHORT).show()}
fun vibrate(ctx: Context) { try{val v=ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)v.vibrate(VibrationEffect.createOneShot(30,VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(30)}catch(_:Exception){} }
fun saveLog(ctx: Context, e: String) { val p=ctx.getSharedPreferences("onenation_logs",Context.MODE_PRIVATE);val a=org.json.JSONArray(p.getString("logs","[]")?:"[]");a.put(e);while(a.length()>500)a.remove(0);p.edit().putString("logs",a.toString()).apply() }
fun loadLogs(ctx: Context): List<String> { val a=org.json.JSONArray(ctx.getSharedPreferences("onenation_logs",Context.MODE_PRIVATE).getString("logs","[]")?:"[]");val l=mutableListOf<String>();for(i in 0 until a.length())l.add(a.getString(i));return l.reversed() }
