package com.phaize.companion

import android.os.Bundle
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
private enum class Tab(val text:String){Home("Home"),Agents("Agents"),Approvals("Approve"),Files("Files"),History("History"),Settings("Settings")}
private fun demo():Snapshot=parseSnapshot("""{"revision":1,"mode":"demo","host":{"name":"PhAize Sample","online":false,"monitoring":"demo","containment":"demo","cpu":0,"memoryPercent":0},
"agents":[{"id":"codex","name":"Codex","status":"working","role":"Implementer","task":"Android gateway integration","cpu":9,"memoryMb":1700},{"id":"claude","name":"Claude Code","status":"reviewing","role":"Reviewer","task":"Security review","cpu":5,"memoryMb":1900}],
"tasks":[{"id":"T-01","title":"Mobile UI and state","agentId":"codex","status":"in_progress","progress":70}],
"approvals":[{"id":"demo-1","revision":1,"agentId":"codex","type":"file_write","title":"Change scheduler","detail":"Example write request","resource":"C:/Projects/PhAize/src/chat/scheduler.ts","risk":"high","expiresAt":"2099-01-01"}],
"files":[{"id":"f1","path":"src/chat/scheduler.ts","action":"modified","agentId":"codex","timestamp":"sample","risk":"medium"}],
"timeline":[{"id":"t1","type":"task","title":"Sample task in progress","detail":"Connect a trusted live desktop gateway for real information","timestamp":"sample","agentId":"codex"}],
"statistics":{"eventsToday":1,"blockedToday":0,"completedToday":0}}""")
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent {
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
 val scope=rememberCoroutineScope()
 val deviceId=remember{UUID.randomUUID().toString()}
 val saved=remember{store.load()}
 var base by remember{mutableStateOf(saved?.first?:"")}
 var token by remember{mutableStateOf(saved?.second?:"")}
 var live by remember{mutableStateOf(false)}
 var snapshot by remember{mutableStateOf(demo())}
 var tab by remember{mutableStateOf(Tab.Home)}
 var notice by remember{mutableStateOf<String?>(null)}
 var busy by remember{mutableStateOf(false)}
 var approvalPending by remember{mutableStateOf<Pair<Approval,Boolean>?>(null)}
 var agentPending by remember{mutableStateOf<Pair<Agent,String>?>(null)}
 suspend fun refresh(){
  try{val new=api.snapshot(base,token);snapshot=new;live=new.mode=="live";if(!live)notice="Demo gateway is not connected to a desktop"}
  catch(e:Exception){live=false;notice="Connection lost: "+(e.message?:"offline")}
 }
 LaunchedEffect(Unit){if(saved!=null){refresh()}}
 LaunchedEffect(live,base,token){
  while(live){delay(8000);refresh()}
 }
 Scaffold(containerColor=bg,topBar={
  Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Column{Text("PhAize",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.headlineSmall,color=accent)
     Text(if(live)"LIVE • Desktop connected" else "DEMO / OFFLINE",color=if(live)green else amber,style=MaterialTheme.typography.labelSmall)}
   IconButton(onClick={scope.launch{if(live)refresh()}}){Icon(Icons.Outlined.Refresh,"Refresh",tint=accent)}
  }
 },bottomBar={
  NavigationBar(containerColor=panel){
   Tab.entries.forEach{t->NavigationBarItem(selected=tab==t,onClick={tab=t},icon={
    val icon=when(t){Tab.Home->Icons.Outlined.Dashboard;Tab.Agents->Icons.Outlined.SmartToy;Tab.Approvals->Icons.Outlined.VerifiedUser;Tab.Files->Icons.Outlined.FolderOpen;Tab.History->Icons.Outlined.History;Tab.Settings->Icons.Outlined.Settings}
    Icon(icon,t.text)
   },label={Text(t.text,style=MaterialTheme.typography.labelSmall)})}
  }
 }){padding->
  Column(Modifier.padding(padding)){
   if(notice!=null)Card{Text(notice!!,color=amber,style=MaterialTheme.typography.bodySmall);TextButton(onClick={notice=null}){Text("Dismiss")}}
   when(tab){
    Tab.Home->Home(snapshot,{tab=it})
    Tab.Agents->Agents(snapshot,live&&snapshot.controlsAvailable){a,action->agentPending=a to action}
    Tab.Approvals->Approvals(snapshot,live&&snapshot.approvalsAvailable){a,ok->approvalPending=a to ok}
    Tab.Files->Files(snapshot)
    Tab.History->History(snapshot)
    Tab.Settings->Settings(base,token,live,{base=it},{token=it},{
      scope.launch{busy=true;try{val s=api.snapshot(base,token);snapshot=s;live=s.mode=="live"
       if(live){store.save(base,token);notice="Live desktop gateway connected";tab=Tab.Home}
       else notice="Demo gateway: real controls remain disabled"
      }catch(e:Exception){live=false;notice="Connection failed: "+(e.message?:"unknown")}finally{busy=false}}
     },{store.clear();token="";base="";live=false;snapshot=demo();notice="Connection removed"},busy)
   }
  }
 }
 if(approvalPending!=null){
  val (a,yes)=approvalPending!!
  AlertDialog(onDismissRequest={approvalPending=null},title={Text(if(yes)"Approve this operation once?" else "Deny this operation?")},
   text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(a.title);TextInfo(a.resource);TextInfo("Agent: ${a.agentId} • Risk: ${a.risk} • Revision: ${a.revision}");Text("The native desktop must re-check this request.",color=amber)}},
   confirmButton={Button(onClick={approvalPending=null;scope.launch{busy=true;try{api.decide(base,token,a,yes,deviceId);refresh();notice="Decision sent to desktop"}catch(e:Exception){notice=e.message};busy=false}},enabled=!busy){Text(if(yes)"Approve once" else "Deny")}},
   dismissButton={TextButton(onClick={approvalPending=null}){Text("Cancel")}})
 }
 if(agentPending!=null){
  val (a,action)=agentPending!!
  AlertDialog(onDismissRequest={agentPending=null},title={Text("Send ${action.replace('_',' ')} to ${a.name}?")},
   text={Text("A native authorization check is required; emergency stop cannot be undone from this screen.")},
   confirmButton={Button(onClick={agentPending=null;scope.launch{busy=true;try{api.control(base,token,a,action,snapshot.revision,deviceId);refresh();notice="Control request sent"}catch(e:Exception){notice=e.message};busy=false}},enabled=!busy){Text("Confirm")}},
   dismissButton={TextButton(onClick={agentPending=null}){Text("Cancel")}})
 }
}
@Composable private fun ListPage(title:String,content:androidx.compose.foundation.lazy.LazyListScope.()->Unit){
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
  item{Title(title)};content()
 }
}
@Composable private fun AgentCard(a:Agent,live:Boolean,onControl:(Agent,String)->Unit){
 Card{
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(a.name,fontWeight=FontWeight.Bold);Tag(a.status,if(a.status=="working")green else accent)}
  TextInfo(a.role+" • "+(a.task?:"No active task"))
  TextInfo("CPU ${a.cpu}%   RAM ${a.memoryMb} MB")
  if(live) Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
   OutlinedButton(onClick={onControl(a,"pause")}){Text("Pause")}
   OutlinedButton(onClick={onControl(a,"resume")}){Text("Resume")}
   Button(onClick={onControl(a,"emergency_stop")},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF843637))){Text("Stop")}
  }
 }
}
@Composable private fun Home(s:Snapshot,go:(Tab)->Unit)=ListPage("Overview"){
 item{Card{Text(s.host.name,fontWeight=FontWeight.Bold);TextInfo("Monitoring ${s.host.monitoring} • Containment ${s.host.containment}");TextInfo("CPU ${s.host.cpu}%  •  Memory ${s.host.memoryPercent}%")}}
 item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Surface(color=panel,shape=RoundedCornerShape(15.dp),modifier=Modifier.weight(1f)){Column(Modifier.padding(12.dp)){Text(s.agents.count{it.status!="idle"}.toString(),color=accent,style=MaterialTheme.typography.headlineMedium);TextInfo("Active agents")}};Surface(color=panel,shape=RoundedCornerShape(15.dp),modifier=Modifier.weight(1f)){Column(Modifier.padding(12.dp)){Text(if(s.approvalsAvailable)s.approvals.size.toString() else "—",color=amber,style=MaterialTheme.typography.headlineMedium);TextInfo("Approvals")}}}}
 item{TextButton(onClick={go(Tab.Approvals)}){Text(if(s.approvalsAvailable)"Open pending approvals" else "Approvals unavailable until native integration")}}
 items(s.tasks){t->Card{Text(t.title,fontWeight=FontWeight.Bold);TextInfo("${t.agentId?:"Unassigned"} • ${t.status}");LinearProgressIndicator(progress={t.progress.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth())}}
 item{TextInfo("Events: ${s.eventsToday}   •   Blocked: ${s.blockedToday}   •   Completed: ${s.completedToday}")}
}
@Composable private fun Agents(s:Snapshot,live:Boolean,onControl:(Agent,String)->Unit){
 var q by remember{mutableStateOf("")}
 ListPage("Agents"){
  if(!live)item{Text("Native agent controls are not connected. Read-only monitoring is available.",color=amber)}
  item{OutlinedTextField(q,{q=it},modifier=Modifier.fillMaxWidth(),label={Text("Find agent")})}
  items(s.agents.filter{it.name.contains(q,true)||it.task?.contains(q,true)==true}){a->AgentCard(a,live,onControl)}
 }
}
@Composable private fun Approvals(s:Snapshot,live:Boolean,onDecision:(Approval,Boolean)->Unit)=ListPage("Permission requests"){
 if(!live)item{Text("Native approvals are not connected. Remote decisions are disabled.",color=amber)}
 if(live&&s.approvals.isEmpty())item{TextInfo("No pending approvals.")}
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
