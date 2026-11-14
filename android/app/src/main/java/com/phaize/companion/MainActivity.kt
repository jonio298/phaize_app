package com.phaize.companion

import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val bg=Color(0xFF08141B)
private val panel=Color(0xFF132732)
private val accent=Color(0xFF69C9D6)
private val muted=Color(0xFF97B0BB)
private val amber=Color(0xFFF1BD72)
private val red=Color(0xFFEA8B8B)
private val green=Color(0xFF82DAAE)
private val scheme=darkColorScheme(primary=accent,background=bg,surface=panel,onSurface=Color(0xFFEAF5F7),onBackground=Color.White)
private enum class Tab(val text:String){Home("Home"),Agents("Agents"),Work("Work"),Security("Security"),More("More") }
private fun demo():Snapshot=parseSnapshot("""{"revision":1,"mode":"demo","host":{"name":"PhAize Sample","online":false,"monitoring":"demo","containment":"demo","cpu":0,"memoryPercent":0},
"agents":[{"id":"codex","name":"Codex","status":"working","role":"Implementer","task":"Android gateway integration","cpu":9,"memoryMb":1700},{"id":"claude","name":"Claude Code","status":"reviewing","role":"Reviewer","task":"Security review","cpu":5,"memoryMb":1900}],
"tasks":[{"id":"T-01","title":"Mobile UI and state","agentId":"codex","status":"in_progress","progress":70}],
"approvals":[{"id":"demo-1","revision":1,"agentId":"codex","type":"file_write","title":"Change scheduler","detail":"Example write request","resource":"C:/Projects/PhAize/src/chat/scheduler.ts","risk":"high","expiresAt":"2099-01-01"}],
"files":[{"id":"f1","path":"src/chat/scheduler.ts","action":"modified","agentId":"codex","timestamp":"sample","risk":"medium"}],
"timeline":[{"id":"t1","type":"task","title":"Sample task in progress","detail":"Connect a trusted live desktop gateway for real information","timestamp":"sample","agentId":"codex"}],
"statistics":{"eventsToday":1,"blockedToday":0,"completedToday":0}}""")
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);LocalAlerts.createChannel(this);setContent {
  MaterialTheme(colorScheme=scheme){Screen(SecureConnectionStore(applicationContext))}
 }}
}
@Composable private fun Card(content:@Composable ColumnScope.()->Unit){
 Surface(color=panel,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){
  Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp),content=content)
 }
}
@Composable private fun Tag(label:String,color:Color=accent){
 Surface(color=color.copy(alpha=.13f),shape=RoundedCornerShape(10.dp)){
  Text(label,color=color,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp))
 }
}
@Composable private fun TextInfo(text:String){Text(text,color=muted,style=MaterialTheme.typography.bodySmall)}
@Composable private fun Title(text:String){Text(text,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
@Composable private fun Screen(store:SecureConnectionStore){
 val api=remember{ApiClient()}
 val context=LocalContext.current
 val notificationPrefs=remember{context.getSharedPreferences("notification_preferences",android.content.Context.MODE_PRIVATE)}
 var notifyEnabled by remember{mutableStateOf(notificationPrefs.getBoolean("enabled",false))}
 val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->notifyEnabled=granted;notificationPrefs.edit().putBoolean("enabled",granted).apply()}
 val scope=rememberCoroutineScope()
 var deviceId by remember{mutableStateOf(store.deviceId())}
 val saved=remember{store.load()}
 var base by remember{mutableStateOf(saved?.first?:"")}
 var token by remember{mutableStateOf(saved?.second?:"")}
 var live by remember{mutableStateOf(false)}
 var snapshot by remember{mutableStateOf(demo())}
 var tab by remember{mutableStateOf(Tab.Home)}
 var notice by remember{mutableStateOf<String?>(null)}
 var busy by remember{mutableStateOf(false)}
 var browserPage by remember{mutableStateOf<BrowserPage?>(null)}
 var errorsPage by remember{mutableStateOf<ErrorPage?>(null)}
 var fileStack by remember{mutableStateOf(listOf<String>())}
 var loadingExtra by remember{mutableStateOf(false)}
 var workView by remember{mutableStateOf("Tasks")}
 var securityView by remember{mutableStateOf("Approvals")}
 var moreView by remember{mutableStateOf("Files")}
 var chatPage by remember{mutableStateOf<ChatPage?>(null)}
 var briefingPage by remember{mutableStateOf<ProjectBriefing?>(null)}
 var buildFeed by remember{mutableStateOf<BuildFeed?>(null)}
 var reviewPage by remember{mutableStateOf<ReviewPage?>(null)}
 var alertPage by remember{mutableStateOf<AlertPage?>(null)}
 var opsPage by remember{mutableStateOf<OpsPage?>(null)}
 var chatAgentId by remember{mutableStateOf<String?>(null)}
 var extraBusy by remember{mutableStateOf(false)}
 var approvalPending by remember{mutableStateOf<Pair<Approval,Boolean>?>(null)}
 var agentPending by remember{mutableStateOf<Pair<Agent,String>?>(null)}
 var lockdownPending by remember{mutableStateOf(false)}
 var policyPending by remember{mutableStateOf<Agent?>(null)}
 suspend fun refresh(){
  try{val new=api.snapshot(base,token);snapshot=new;live=new.mode=="live";if(!live)notice="Demo gateway is not connected to a desktop"}
  catch(e:Exception){live=false;notice="Connection lost: "+(e.message?:"offline")}
 }
 LaunchedEffect(Unit){if(saved!=null){refresh()}}
 LaunchedEffect(tab,workView,securityView,moreView,live,base,chatAgentId){
  if(live){
   if(tab==Tab.More&&moreView=="Files"){loadingExtra=true;try{browserPage=api.browse(base,token,fileStack.lastOrNull())}catch(e:Exception){browserPage=null;notice="File browser: "+(e.message?:"unavailable")}finally{loadingExtra=false}}
   if(tab==Tab.More&&moreView=="Tools"){extraBusy=true;try{opsPage=api.operations(base,token)}catch(e:Exception){opsPage=null;notice="Operations: "+(e.message?:"unavailable")}finally{extraBusy=false}}
   if(tab==Tab.More&&moreView=="Logs"){loadingExtra=true;try{errorsPage=api.errors(base,token)}catch(e:Exception){errorsPage=null;notice="Error log: "+(e.message?:"unavailable")}finally{loadingExtra=false}}
   if(tab==Tab.Work&&workView=="Chat"&&chatAgentId!=null){extraBusy=true;try{chatPage=api.chat(base,token,chatAgentId!!)}catch(e:Exception){chatPage=null;notice="Chat: "+(e.message?:"unavailable")}finally{extraBusy=false}}
   if(tab==Tab.Work&&workView=="Reviews"){extraBusy=true;try{reviewPage=api.reviews(base,token)}catch(e:Exception){reviewPage=null;notice="Reviews: "+(e.message?:"unavailable")}finally{extraBusy=false}}
   if(tab==Tab.Work&&workView=="Briefing"){extraBusy=true;try{briefingPage=api.briefing(base,token)}catch(e:Exception){briefingPage=null;notice="Briefing: "+(e.message?:"unavailable")}finally{extraBusy=false}}
   if(tab==Tab.Work&&workView=="Builds"){extraBusy=true;try{buildFeed=api.builds(base,token)}catch(e:Exception){buildFeed=null;notice="Builds: "+(e.message?:"unavailable")}finally{extraBusy=false}}
   if(tab==Tab.Security&&securityView=="Alerts"){extraBusy=true;try{alertPage=api.alerts(base,token)}catch(e:Exception){alertPage=null;notice="Alerts: "+(e.message?:"unavailable")}finally{extraBusy=false}}
  }
 }
 LaunchedEffect(live,notifyEnabled,base,token){
  if(live&&notifyEnabled){
   var seen=emptySet<String>()
   var initialized=false
   while(live&&notifyEnabled){
    try{
     val incoming=api.alerts(base,token)
     val current=incoming.alerts.map{it.id}.toSet()
     if(initialized)incoming.alerts.filter{it.id !in seen}.forEach{LocalAlerts.notify(context,it)}
     seen=current;initialized=true
    }catch(_:Exception){initialized=false}
    delay(15000)
   }
  }
 }
 LaunchedEffect(live,base,token){
  while(live){delay(8000);refresh()}
 }
 Scaffold(containerColor=bg,topBar={
  Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Column{Text("PhAize",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.headlineSmall,color=accent)
     Text(if(live)"LIVE • Desktop connected" else "DEMO / OFFLINE",color=if(live)green else amber,style=MaterialTheme.typography.labelSmall)}
   Row{
    IconButton(onClick={scope.launch{if(live){refresh();if(tab==Tab.Security&&securityView=="Alerts")try{alertPage=api.alerts(base,token)}catch(e:Exception){notice=e.message};if(tab==Tab.Work&&workView=="Chat"&&chatAgentId!=null)try{chatPage=api.chat(base,token,chatAgentId!!)}catch(e:Exception){notice=e.message};if(tab==Tab.Work&&workView=="Reviews")try{reviewPage=api.reviews(base,token)}catch(e:Exception){notice=e.message};if(tab==Tab.More&&moreView=="Tools")try{opsPage=api.operations(base,token)}catch(e:Exception){notice=e.message};if(tab==Tab.More&&moreView=="Logs")try{errorsPage=api.errors(base,token)}catch(e:Exception){notice=e.message};if(tab==Tab.More&&moreView=="Files")try{browserPage=api.browse(base,token,fileStack.lastOrNull())}catch(e:Exception){notice=e.message}}}}){Icon(Icons.Outlined.Refresh,"Refresh",tint=accent)}
    IconButton(onClick={lockdownPending=true},enabled=live){Icon(Icons.Outlined.GppBad,"Emergency lockdown",tint=if(live)red else muted)}
    IconButton(onClick={tab=Tab.More;moreView="Settings"}){Icon(Icons.Outlined.Settings,"Connection settings",tint=accent)}
   }
  }
 },bottomBar={
  NavigationBar(containerColor=panel){
   Tab.entries.forEach{t->NavigationBarItem(selected=tab==t,onClick={tab=t},icon={
    val icon=when(t){Tab.Home->Icons.Outlined.Dashboard;Tab.Agents->Icons.Outlined.SmartToy;Tab.Work->Icons.Outlined.Assignment;Tab.Security->Icons.Outlined.VerifiedUser;Tab.More->Icons.Outlined.MoreHoriz}
    Icon(icon,t.text)
   },label={Text(t.text,style=MaterialTheme.typography.labelSmall)})}
  }
 }){padding->
  Column(Modifier.padding(padding)){
   if(notice!=null)Card{Text(notice!!,color=amber,style=MaterialTheme.typography.bodySmall);TextButton(onClick={notice=null}){Text("Dismiss")}}
   when(tab){
    Tab.Home->Home(snapshot,{t->tab=when(t){Tab.Home->Tab.Home;else->Tab.Security};securityView="Approvals"})
    Tab.Agents->Agents(snapshot,live,{a,action->agentPending=a to action},{a->policyPending=a})
    Tab.Work->{
     Column{
      Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=10.dp)){listOf("Tasks","Goals","Chat","Reviews","Briefing","Builds").forEach{v->FilterChip(selected=workView==v,onClick={workView=v},label={Text(v)},modifier=Modifier.padding(end=6.dp))}}
      Box(Modifier.weight(1f)){
       when(workView){
        "Tasks"->TasksScreen(snapshot,live){title,description,agent,priority->scope.launch{busy=true;try{api.createTask(base,token,title,description,agent,priority,snapshot.revision,deviceId);refresh();notice="Desktop accepted new task"}catch(e:Exception){notice="Task failed: "+(e.message?:"unavailable")}finally{busy=false}}}
        "Goals"->GoalScreen(live,busy){goal,instructions,priority->scope.launch{busy=true;try{api.createGoal(base,token,goal,instructions,priority,snapshot.revision,deviceId);refresh();notice="Desktop accepted goal"}catch(e:Exception){notice="Delegation failed: "+(e.message?:"unavailable")}finally{busy=false}}}
        "Chat"->AgentChatScreen(snapshot.agents,chatAgentId,live,extraBusy,chatPage,{id->chatAgentId=id;chatPage=null},{id,msg->scope.launch{extraBusy=true;try{api.sendChat(base,token,id,msg,snapshot.revision,deviceId);chatPage=api.chat(base,token,id);notice="Message accepted by desktop"}catch(e:Exception){notice="Message failed: "+(e.message?:"unavailable")}finally{extraBusy=false}}})
        "Reviews"->CodeReviewScreen(live,extraBusy,reviewPage){item,decision->scope.launch{extraBusy=true;try{api.reviewDecision(base,token,item.id,decision,item.revision,deviceId);reviewPage=api.reviews(base,token);notice="Desktop accepted review decision"}catch(e:Exception){notice="Review failed: "+(e.message?:"unavailable")}finally{extraBusy=false}}}
        "Briefing"->BriefingScreen(live,extraBusy,briefingPage){scope.launch{extraBusy=true;try{briefingPage=api.briefing(base,token)}catch(e:Exception){notice=e.message?:"Briefing unavailable"}finally{extraBusy=false}}}
        "Builds"->BuildMonitorScreen(live,extraBusy,buildFeed){scope.launch{extraBusy=true;try{buildFeed=api.builds(base,token)}catch(e:Exception){notice=e.message?:"Build feed unavailable"}finally{extraBusy=false}}}
       }
      }
     }
    }
    Tab.Security->Column{
     Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
      Text("Urgent phone alerts",style=MaterialTheme.typography.labelMedium)
      Switch(checked=notifyEnabled,onCheckedChange={checked->
       if(!checked){notifyEnabled=false;notificationPrefs.edit().putBoolean("enabled",false).apply()}
       else if(LocalAlerts.permitted(context)){notifyEnabled=true;notificationPrefs.edit().putBoolean("enabled",true).apply()}
       else if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
      })
     }
     Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=10.dp)){listOf("Approvals","Alerts").forEach{v->FilterChip(selected=securityView==v,onClick={securityView=v},label={Text(v)},modifier=Modifier.padding(end=6.dp))}}
     Box(Modifier.weight(1f)){
      when(securityView){
       "Approvals"->Approvals(snapshot,live){a,ok->approvalPending=a to ok}
       "Alerts"->AlertsScreen(live,extraBusy,alertPage){securityView="Approvals"}
      }
     }
    }
    Tab.More->Column{
     Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=10.dp)){listOf("Files","Logs","Tools","Settings").forEach{v->FilterChip(selected=moreView==v,onClick={moreView=v},label={Text(v)},modifier=Modifier.padding(end=6.dp))}}
     Box(Modifier.weight(1f)){
      when(moreView){
       "Files"->FileCenterScreen(snapshot,live,browserPage,fileStack,loadingExtra,{id->fileStack=fileStack+id;scope.launch{loadingExtra=true;try{browserPage=api.browse(base,token,id)}catch(e:Exception){notice=e.message?:"Browse unavailable"}finally{loadingExtra=false}}},{fileStack=fileStack.dropLast(1);scope.launch{loadingExtra=true;try{browserPage=api.browse(base,token,fileStack.lastOrNull())}catch(e:Exception){notice=e.message?:"Browse unavailable"}finally{loadingExtra=false}}})
       "Logs"->LogCenterScreen(snapshot,live,errorsPage,loadingExtra)
       "Tools"->OperationsScreen(live,extraBusy,opsPage,snapshot.agents,{scope.launch{extraBusy=true;try{opsPage=api.operations(base,token)}catch(e:Exception){notice=e.message?:"Operations unavailable"}finally{extraBusy=false}}},{action,parameters->scope.launch{extraBusy=true;try{api.opsAction(base,token,action,parameters,snapshot.revision,deviceId);opsPage=api.operations(base,token);notice="Native desktop accepted "+action.replace('_',' ')}catch(e:Exception){notice="Request rejected: "+(e.message?:"unavailable")}finally{extraBusy=false}}})
       "Settings"->Settings(base,token,live,{base=it},{token=it},{
        scope.launch{busy=true;try{val result=api.snapshot(base,token);snapshot=result;live=result.mode=="live";if(live){store.save(base,token);notice="Live desktop gateway connected";tab=Tab.Home}else notice="Demo gateway: real controls disabled"}catch(e:Exception){live=false;notice="Connection failed: "+(e.message?:"unknown")}finally{busy=false}}
       },{store.clear();deviceId=store.deviceId();token="";base="";live=false;snapshot=demo();browserPage=null;errorsPage=null;chatPage=null;reviewPage=null;alertPage=null;opsPage=null;briefingPage=null;buildFeed=null;fileStack=emptyList();notice="Connection removed"},busy)
      }
     }
    }
   }
  }
 }
 if(lockdownPending){
  AlertDialog(onDismissRequest={lockdownPending=false},
   title={Text("Emergency security lockdown")},
   text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("Request an immediate desktop-wide agent lockdown. The native coordinator must enforce this; the phone cannot disable MXC or bypass policy.")
    Text("Desktop: ${snapshot.host.name} • Monitoring: ${snapshot.host.monitoring}",color=amber)
    Text("This may interrupt active tasks and agents.",color=red)
   }},
   confirmButton={Button(enabled=live&&!busy,onClick={lockdownPending=false;scope.launch{
    busy=true
    try{
     api.opsAction(base,token,"lockdown",org.json.JSONObject().put("reason","User requested emergency lockdown from paired Android companion"),snapshot.revision,deviceId)
     refresh();notice="Desktop acknowledged emergency lockdown request"
    }catch(e:Exception){notice="Lockdown could not be confirmed: "+(e.message?:"desktop unavailable")}
    finally{busy=false}
   }},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF843637))){Text("Request lockdown")}},
   dismissButton={TextButton(onClick={lockdownPending=false}){Text("Cancel")}}
  )
 }
 if(approvalPending!=null){
  val (a,yes)=approvalPending!!
  AlertDialog(onDismissRequest={approvalPending=null},title={Text(if(yes)"Approve this operation once?" else "Deny this operation?")},
   text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(a.title);TextInfo(a.resource);TextInfo("Agent: ${a.agentId} • Risk: ${a.risk} • Revision: ${a.revision}");Text("The native desktop must re-check this request.",color=amber)}},
   confirmButton={Button(onClick={approvalPending=null;scope.launch{busy=true;try{api.decide(base,token,a,yes,deviceId);refresh();notice="Desktop accepted approval decision"}catch(e:Exception){notice=e.message};busy=false}},enabled=!busy){Text(if(yes)"Approve once" else "Deny")}},
   dismissButton={TextButton(onClick={approvalPending=null}){Text("Cancel")}})
 }
 if(policyPending!=null){
  val a=policyPending!!
  var read by remember(a.id){mutableStateOf(a.readPermission.takeIf{it in listOf("allow","ask","deny") }?:"allow")}
  var write by remember(a.id){mutableStateOf(a.writePermission.takeIf{it in listOf("ask","deny") }?:"ask")}
  var delete by remember(a.id){mutableStateOf(a.deletePermission.takeIf{it in listOf("ask","deny") }?:"ask")}
  AlertDialog(onDismissRequest={policyPending=null},title={Text("Permissions • ${a.name}")},
   text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
    TextInfo("Changes require native desktop authorization. Mobile cannot enable automatic writes or deletes.")
    Text("Read",fontWeight=FontWeight.Bold)
    Row{listOf("allow","ask","deny").forEach{v->FilterChip(selected=read==v,onClick={read=v},label={Text(v)},modifier=Modifier.padding(end=5.dp))}}
    Text("Write",fontWeight=FontWeight.Bold)
    Row{listOf("ask","deny").forEach{v->FilterChip(selected=write==v,onClick={write=v},label={Text(v)},modifier=Modifier.padding(end=5.dp))}}
    Text("Delete",fontWeight=FontWeight.Bold)
    Row{listOf("ask","deny").forEach{v->FilterChip(selected=delete==v,onClick={delete=v},label={Text(v)},modifier=Modifier.padding(end=5.dp))}}
   }},
   confirmButton={Button(onClick={policyPending=null;scope.launch{busy=true;try{api.permissions(base,token,a,read,write,delete,snapshot.revision,deviceId);refresh();notice="Desktop accepted permission request"}catch(e:Exception){notice=e.message?:"Permission change failed"}finally{busy=false}}},enabled=live&&!busy){Text("Apply")}},
   dismissButton={TextButton(onClick={policyPending=null}){Text("Cancel")}})
 }
 if(agentPending!=null){
  val (a,action)=agentPending!!
  AlertDialog(onDismissRequest={agentPending=null},title={Text("Send ${action.replace('_',' ')} to ${a.name}?")},
   text={Text("A native authorization check is required; emergency stop cannot be undone from this screen.")},
   confirmButton={Button(onClick={agentPending=null;scope.launch{busy=true;try{api.control(base,token,a,action,snapshot.revision,deviceId);refresh();notice="Desktop accepted control request"}catch(e:Exception){notice=e.message};busy=false}},enabled=!busy){Text("Confirm")}},
   dismissButton={TextButton(onClick={agentPending=null}){Text("Cancel")}})
 }
}
@Composable private fun ListPage(title:String,content:androidx.compose.foundation.lazy.LazyListScope.()->Unit){
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
  item{Title(title)};content()
 }
}
@Composable private fun AgentCard(a:Agent,live:Boolean,onControl:(Agent,String)->Unit,onPermissions:(Agent)->Unit){
 Card{
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(a.name,fontWeight=FontWeight.Bold);Tag(a.status,if(a.status=="working")green else accent)}
  TextInfo(a.role+" • "+(a.task?:"No active task"))
  TextInfo("CPU ${a.cpu}%   RAM ${a.memoryMb} MB")
  if(live){
   Row(modifier=Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
    OutlinedButton(onClick={onControl(a,"start")},enabled=a.status in listOf("idle","stopped","exited")){Text("Start")}
    OutlinedButton(onClick={onControl(a,"pause")},enabled=a.status !in listOf("paused","stopped","idle","exited")){Text("Pause")}
    OutlinedButton(onClick={onControl(a,"resume")},enabled=a.status=="paused"){Text("Resume")}
    Button(onClick={onControl(a,"emergency_stop")},enabled=a.status !in listOf("stopped","idle","exited"),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF843637))){Text("Stop")}
   }
   OutlinedButton(onClick={onPermissions(a)}){Text("Permissions")}
   TextInfo("Read: ${a.readPermission}  •  Write: ${a.writePermission}  •  Delete: ${a.deletePermission}")
  }
 }
}
@Composable private fun Home(s:Snapshot,go:(Tab)->Unit)=ListPage("Overview"){
 item{Card{Text(s.host.name,fontWeight=FontWeight.Bold);TextInfo("Monitoring ${s.host.monitoring} • Containment ${s.host.containment}");TextInfo("CPU ${s.host.cpu}%  •  Memory ${s.host.memoryPercent}%")}}
 item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Surface(color=panel,shape=RoundedCornerShape(15.dp),modifier=Modifier.weight(1f)){Column(Modifier.padding(12.dp)){Text(s.agents.count{it.status!="idle"}.toString(),color=accent,style=MaterialTheme.typography.headlineMedium);TextInfo("Active agents")}};Surface(color=panel,shape=RoundedCornerShape(15.dp),modifier=Modifier.weight(1f)){Column(Modifier.padding(12.dp)){Text(s.approvals.size.toString(),color=amber,style=MaterialTheme.typography.headlineMedium);TextInfo("Approvals")}}}}
 item{TextButton(onClick={go(Tab.Security)}){Text("Open pending approvals")}}
 items(s.tasks){t->Card{Text(t.title,fontWeight=FontWeight.Bold);TextInfo("${t.agentId?:"Unassigned"} • ${t.status}");LinearProgressIndicator(progress={t.progress.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth())}}
 item{TextInfo("Events: ${s.eventsToday}   •   Blocked: ${s.blockedToday}   •   Completed: ${s.completedToday}")}
}
@Composable private fun Agents(s:Snapshot,live:Boolean,onControl:(Agent,String)->Unit,onPermissions:(Agent)->Unit){
 var q by remember{mutableStateOf("")}
 ListPage("Agents"){
  item{OutlinedTextField(q,{q=it},modifier=Modifier.fillMaxWidth(),label={Text("Find agent")})}
  items(s.agents.filter{it.name.contains(q,true)||it.task?.contains(q,true)==true}){a->AgentCard(a,live,onControl,onPermissions)}
 }
}
@Composable private fun Approvals(s:Snapshot,live:Boolean,onDecision:(Approval,Boolean)->Unit)=ListPage("Permission requests"){
 if(!live)item{Text("Demo data only; approvals disabled.",color=amber)}
 if(s.approvals.isEmpty())item{TextInfo("No pending approvals.")}
 items(s.approvals){a->Card{
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(a.title,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Tag(a.risk,if(a.risk=="critical")red else amber)}
  TextInfo(a.detail);Text(a.resource,color=accent,style=MaterialTheme.typography.bodySmall)
  TextInfo("${a.agentId} • ${a.type} • Expires: ${a.expiresAt}")
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedButton(onClick={onDecision(a,false)},enabled=live,modifier=Modifier.weight(1f)){Text("Deny")}
   Button(onClick={onDecision(a,true)},enabled=live,modifier=Modifier.weight(1f)){Text("Approve once")}
  }
 }}
}
@Composable private fun Files(s:Snapshot){
 var q by remember{mutableStateOf("")}
 var filter by remember{mutableStateOf("All")}
 ListPage("File activity"){
  item{OutlinedTextField(q,{q=it},modifier=Modifier.fillMaxWidth(),label={Text("Search path / agent")})}
  item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("All","read","modified","created").forEach{f->FilterChip(selected=filter==f,onClick={filter=f},label={Text(f)})}}}
  items(s.files.filter{(filter=="All"||it.action==filter)&&(it.path.contains(q,true)||it.agentId.contains(q,true))}){f->Card{Tag(f.action);Text(f.path,fontWeight=FontWeight.Medium);TextInfo("${f.agentId} • ${f.timestamp} • ${f.risk}")}}
 }
}
@Composable private fun History(s:Snapshot){
 var q by remember{mutableStateOf("")}
 ListPage("Timeline & changelog"){
  item{OutlinedTextField(q,{q=it},modifier=Modifier.fillMaxWidth(),label={Text("Search events")})}
  items(s.timeline.filter{it.title.contains(q,true)||it.detail.contains(q,true)||it.type.contains(q,true)}){e->Card{Tag(e.type);Text(e.title,fontWeight=FontWeight.Bold);TextInfo(e.detail);TextInfo("${e.timestamp} • ${e.agentId?:"System"}")}}
 }
}
@Composable private fun Settings(base:String,token:String,live:Boolean,setBase:(String)->Unit,setToken:(String)->Unit,connect:()->Unit,disconnect:()->Unit,busy:Boolean)=ListPage("Connection"){
 item{Card{
  Text("Trusted HTTPS desktop gateway",fontWeight=FontWeight.Bold)
  TextInfo("Use a private HTTPS tunnel or authenticated reverse proxy. Do not expose desktop localhost over public HTTP.")
  OutlinedTextField(base,setBase,modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("https://your-private-gateway")})
  OutlinedTextField(token,setToken,modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Gateway token")},visualTransformation=PasswordVisualTransformation())
  Button(onClick=connect,enabled=!busy&&base.startsWith("https://")&&token.isNotBlank()){Text("Connect securely")}
  OutlinedButton(onClick=disconnect){Text("Disconnect / forget token")}
  TextInfo(if(live)"Live data enabled" else "Disconnected or demo")
  TextInfo("Credentials are encrypted with Android Keystore. Native authorization always remains on PhAize desktop.")
 }}
}
