package com.phaize.companion

import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private enum class Tab(val title:String,val subtitle:String,val short:String,val icon:ImageVector){
 Home("Overview","What your agents are doing","Home",Icons.Outlined.Dashboard),
 Agents("Agents","Who is working, and on what","Agents",Icons.Outlined.SmartToy),
 Approvals("Approvals","Permission requests waiting for you","Approve",Icons.Outlined.VerifiedUser),
 Chats("Chats","Read agent conversations","Chats",Icons.Outlined.ChatBubbleOutline),
 Files("Files","Recent file activity","Files",Icons.Outlined.FolderOpen),
 History("History","Timeline and changelog","History",Icons.Outlined.History),
 Settings("Settings","How the app looks and connects","Settings",Icons.Outlined.Settings)
}
private fun demo():Snapshot=parseSnapshot("""{"revision":1,"mode":"demo","host":{"name":"PhAize Sample","online":false,"monitoring":"demo","containment":"demo","cpu":0,"memoryPercent":0},
"agents":[{"id":"codex","name":"Codex","status":"working","role":"Implementer","task":"Android gateway integration","cpu":9,"memoryMb":1700},{"id":"claude","name":"Claude Code","status":"reviewing","role":"Reviewer","task":"Security review","cpu":5,"memoryMb":1900}],
"tasks":[{"id":"T-01","title":"Mobile UI and state","agentId":"codex","status":"in_progress","progress":70}],
"approvals":[{"id":"demo-1","revision":1,"agentId":"codex","type":"file_write","title":"Change scheduler","detail":"Example write request","resource":"C:/Projects/PhAize/src/chat/scheduler.ts","risk":"high","expiresAt":"2099-01-01"}],
"files":[{"id":"f1","path":"src/chat/scheduler.ts","action":"modified","agentId":"codex","timestamp":"sample","risk":"medium"}],
"timeline":[{"id":"t1","type":"task","title":"Sample task in progress","detail":"Connect a trusted live desktop gateway for real information","timestamp":"sample","agentId":"codex"}],
"statistics":{"eventsToday":1,"blockedToday":0,"completedToday":0}}""")

class MainActivity:ComponentActivity(){
 // A phaize://pair link opened from the QR (camera app) arrives here, also while the app is already running.
 private var incomingPairLink by mutableStateOf("")
 override fun onNewIntent(intent:android.content.Intent){super.onNewIntent(intent);setIntent(intent);incomingPairLink=intent.dataString.orEmpty()}
 override fun onCreate(savedInstanceState:Bundle?){
  // Draw behind the status and navigation bars; the header and nav bar add their own insets so nothing is hidden.
  enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
  super.onCreate(savedInstanceState)
  val prefs=UiPrefs(applicationContext)
  incomingPairLink=intent?.dataString.orEmpty()
  Pal.useTheme(prefs.theme)
  val systemReduced=AndroidSettings.Global.getFloat(contentResolver,AndroidSettings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
  setContent {
   val scheme=darkColorScheme(primary=Pal.accent,onPrimary=Color(0xFF031014),background=Pal.bg,surface=Pal.panel,onSurface=Pal.text,onBackground=Pal.text,
    surfaceVariant=Pal.panelHi,onSurfaceVariant=Pal.muted,outline=Pal.border)
   val density=LocalDensity.current
   MaterialTheme(colorScheme=scheme){
    CompositionLocalProvider(LocalAnim provides (prefs.animations&&!systemReduced),LocalDensity provides Density(density.density,density.fontScale*prefs.textScale)){
     Screen(SecureConnectionStore(applicationContext),prefs,incomingPairLink)
    }
   }
  }
 }
}

@Composable private fun StatusChip(live:Boolean,connected:Boolean){
 val shape=RoundedCornerShape(12.dp)
 Row(Modifier.clip(shape).background(Pal.panel).border(1.dp,Pal.border,shape).padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
  LiveDot(live)
  Text(if(live)"LIVE" else if(connected)"DEMO" else "OFFLINE",color=if(live)Pal.green else Pal.amber,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
 }
}

/** The title area, after the desktop header: an icon box, the page title and subtitle, and the connection status. It stays below the status bar. */
@Composable private fun AppHeader(tab:Tab,live:Boolean,connected:Boolean,onRefresh:()->Unit){
 val on=LocalAnim.current
 var turns by remember{mutableStateOf(0f)}
 val spin by animateFloatAsState(turns,motion(on,tween(700,easing=FastOutSlowInEasing)),label="spin")
 val shape=RoundedCornerShape(16.dp)
 Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)).padding(horizontal=16.dp,vertical=12.dp),
  verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  Box(Modifier.size(48.dp).shadow(12.dp,shape,ambientColor=Pal.accent,spotColor=Pal.accent).clip(shape).background(Brush.verticalGradient(listOf(Pal.panelHi,Pal.panel))).border(1.dp,Pal.accent.copy(alpha=.6f),shape),contentAlignment=Alignment.Center){
   AnimatedContent(targetState=tab.icon,transitionSpec={if(on)fadeIn(tween(200)) togetherWith fadeOut(tween(120)) else EnterTransition.None togetherWith ExitTransition.None},label="headerIcon"){icon->
    Icon(icon,null,tint=Pal.accent)
   }
  }
  AnimatedContent(targetState=tab,modifier=Modifier.weight(1f),transitionSpec={if(on)fadeIn(tween(220)) togetherWith fadeOut(tween(120)) else EnterTransition.None togetherWith ExitTransition.None},label="headerTitle"){t->
   Column{
    Text(t.title,color=Pal.text,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    Info(t.subtitle)
   }
  }
  StatusChip(live,connected)
  IconButton(onClick={turns+=360f;onRefresh()}){Icon(Icons.Outlined.Refresh,"Refresh",tint=Pal.accent,modifier=Modifier.graphicsLayer{rotationZ=spin})}
 }
}

@Composable private fun Screen(store:SecureConnectionStore,prefs:UiPrefs,incomingInvite:String){
 val api=remember{ApiClient().also{it.lanFingerprint=store.loadLanPin()}}
 val scope=rememberCoroutineScope()
 val anim=LocalAnim.current
 val deviceId=remember{UUID.randomUUID().toString()}
 val saved=remember{store.load()}
 var base by remember{mutableStateOf(saved?.first?:"")}
 var token by remember{mutableStateOf(saved?.second?:"")}
 var live by remember{mutableStateOf(false)}
 var connected by remember{mutableStateOf(false)}
 var snapshot by remember{mutableStateOf(demo())}
 var tab by remember(incomingInvite){mutableStateOf(if(incomingInvite.startsWith("phaize://pair?"))Tab.Settings else Tab.Home)}
 var pairingLink by remember(incomingInvite){mutableStateOf(incomingInvite)}
 var chatsSupported by remember{mutableStateOf(true)}
 var notice by remember{mutableStateOf<String?>(null)}
 var busy by remember{mutableStateOf(false)}
 var chatList by remember{mutableStateOf(demoChats())}
 var openChat by remember{mutableStateOf<ChatSummary?>(null)}
 var chatMessages by remember{mutableStateOf<List<ChatMessage>>(emptyList())}
 var chatLoading by remember{mutableStateOf(false)}
 var approvalPending by remember{mutableStateOf<Pair<Approval,Boolean>?>(null)}
 var agentPending by remember{mutableStateOf<Pair<Agent,String>?>(null)}
 suspend fun refresh(){
  try{val new=api.snapshot(base,token);snapshot=new;connected=true;live=new.mode=="live";if(!live)notice="Desktop did not confirm a live session" else notice=null}
  catch(e:Exception){live=false;connected=false;snapshot=snapshot.copy(agents=emptyList(),tasks=emptyList(),files=emptyList(),timeline=emptyList(),approvals=emptyList());notice="Local connection unavailable: "+(e.message?:"offline")}
 }
 suspend fun refreshChats(){
  try{chatList=api.chats(base,token);chatsSupported=true}
  catch(e:Exception){val m=e.message?:"";if(m.startsWith("HTTP 404")||m.startsWith("HTTP 503"))chatsSupported=false else notice="Chats unavailable: "+m.ifBlank{"offline"}}
 }
 suspend fun refreshChat(id:String){
  chatLoading=true
  try{chatMessages=api.chat(base,token,id)}catch(e:Exception){notice="Chat unavailable: "+(e.message?:"offline")}finally{chatLoading=false}
 }
 LaunchedEffect(Unit){if(saved!=null&&store.loadLanPin()!=null){refresh()}}
 LaunchedEffect(base,token){
  // Keep polling while a paired desktop is configured, so a dropped connection recovers by itself.
  while(base.isNotBlank()&&token.isNotBlank()&&api.lanFingerprint!=null){delay(8000);refresh()}
 }
 LaunchedEffect(connected){if(!connected){chatList=demoChats()}}
 LaunchedEffect(connected,tab,openChat?.id){
  if(connected&&tab==Tab.Chats){
   while(true){refreshChats();if(!chatsSupported)break;openChat?.let{refreshChat(it.id)};delay(8000)}
  }
 }
 val shownMessages=if(connected&&chatsSupported)chatMessages else (openChat?.let{demoMessages(it.id)}?:emptyList())
 val chatBanner=when{!connected->"Showing sample conversations. Pair with your PC in Settings first.";!chatsSupported->"This desktop does not share chats with the phone yet. Showing sample conversations.";!live->"Demo gateway: these conversations are samples.";else->null}
 val navItems=Tab.entries.map{NavItem(it.short,it.icon,if(it==Tab.Approvals&&live&&snapshot.approvalsAvailable)snapshot.approvals.size else 0)}
 Box(Modifier.fillMaxSize().phaizeBackground()){
  Scaffold(containerColor=Color.Transparent,topBar={AppHeader(tab,live,connected){scope.launch{if(connected)refresh()}}},
   bottomBar={GlassNavBar(navItems,tab.ordinal){tab=Tab.entries[it]}}){padding->
   Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()){
    AnimatedVisibility(visible=notice!=null,enter=if(anim)fadeIn()+expandVertically() else EnterTransition.None,exit=if(anim)fadeOut()+shrinkVertically() else ExitTransition.None){
     Glass(Modifier.padding(horizontal=16.dp,vertical=4.dp)){
      Text(notice?:"",color=Pal.amber,style=MaterialTheme.typography.bodySmall)
      TextButton(onClick={notice=null}){Text("Dismiss",color=Pal.accent)}
     }
    }
    AnimatedContent(targetState=tab,modifier=Modifier.weight(1f),transitionSpec={
     if(!anim)EnterTransition.None togetherWith ExitTransition.None
     else{
      val dir=if(targetState.ordinal>initialState.ordinal)1 else -1
      (fadeIn(tween(260))+slideInHorizontally(tween(300,easing=FastOutSlowInEasing)){it/8*dir}) togetherWith (fadeOut(tween(140))+slideOutHorizontally(tween(260)){-it/10*dir})
     }
    },label="tab"){t->
     when(t){
      Tab.Home->Home(snapshot,{tab=it})
      Tab.Agents->Agents(snapshot,live&&snapshot.controlsAvailable){a,action->agentPending=a to action}
      Tab.Approvals->Approvals(snapshot,live&&snapshot.approvalsAvailable){a,ok->approvalPending=a to ok}
      Tab.Chats->ChatsPage(chatBanner,if(connected&&chatsSupported)chatList.chats else demoChats().chats,openChat,shownMessages,chatLoading,{c->chatMessages=emptyList();openChat=c},{openChat=null})
      Tab.Files->Files(snapshot)
      Tab.History->History(snapshot)
      Tab.Settings->SettingsPage(prefs,pairingLink,{pairingLink=it},live,busy,{
        scope.launch{
         busy=true
         try{
          val invite=LanInvitation.parse(pairingLink)
          val bearer=api.enroll(invite)
          val state=api.snapshot(invite.base,bearer)
          require(state.mode=="live"){"Desktop did not report live monitoring"}
          store.save(invite.base,bearer,invite.fingerprint)
          base=invite.base;token=bearer;snapshot=state;connected=true;live=true
          notice="Paired over local Wi-Fi (read-only)";tab=Tab.Home
          pairingLink=""
         }catch(e:Exception){live=false;connected=false;notice="Pairing failed: "+(e.message?:"unknown")}
         finally{busy=false}
        }
       },{
        store.clear();api.lanFingerprint=null;token="";base="";live=false;connected=false
        snapshot=demo();openChat=null
        notice="Phone disconnected. Revoke it on the Windows desktop to invalidate its access."
       })
     }
    }
   }
  }
 }
 if(approvalPending!=null){
  val (a,yes)=approvalPending!!
  AlertDialog(onDismissRequest={approvalPending=null},containerColor=Pal.panelHi,titleContentColor=Pal.text,textContentColor=Pal.muted,
   title={Text(if(yes)"Approve this operation once?" else "Deny this operation?")},
   text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(a.title,color=Pal.text);Info(a.resource);Info("Agent: ${a.agentId} • Risk: ${a.risk} • Revision: ${a.revision}");Text("The native desktop must re-check this request.",color=Pal.amber)}},
   confirmButton={Button(onClick={approvalPending=null;scope.launch{busy=true;try{api.decide(base,token,a,yes,deviceId);refresh();notice="Decision sent to desktop"}catch(e:Exception){notice=e.message};busy=false}},enabled=!busy){Text(if(yes)"Approve once" else "Deny")}},
   dismissButton={TextButton(onClick={approvalPending=null}){Text("Cancel",color=Pal.accent)}})
 }
 if(agentPending!=null){
  val (a,action)=agentPending!!
  AlertDialog(onDismissRequest={agentPending=null},containerColor=Pal.panelHi,titleContentColor=Pal.text,textContentColor=Pal.muted,
   title={Text("Send ${action.replace('_',' ')} to ${a.name}?")},
   text={Text("A native authorization check is required; emergency stop cannot be undone from this screen.")},
   confirmButton={Button(onClick={agentPending=null;scope.launch{busy=true;try{api.control(base,token,a,action,snapshot.revision,deviceId);refresh();notice="Control request sent"}catch(e:Exception){notice=e.message};busy=false}},enabled=!busy){Text("Confirm")}},
   dismissButton={TextButton(onClick={agentPending=null}){Text("Cancel",color=Pal.accent)}})
 }
}

@Composable private fun ListPage(content:LazyListScope.()->Unit){
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}

@Composable private fun StatCard(modifier:Modifier,value:Int?,label:String,colour:Color){
 val on=LocalAnim.current
 val shown by animateIntAsState(value?:0,motion(on,tween(800,easing=FastOutSlowInEasing)),label="count")
 Glass(modifier){
  Text(if(value==null)"—" else shown.toString(),color=colour,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
  Info(label)
 }
}

@Composable private fun AgentCard(index:Int,a:Agent,live:Boolean,onControl:(Agent,String)->Unit){
 Glass(Modifier.enter(index)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(a.name,color=Pal.text,fontWeight=FontWeight.Bold);Tag(a.status,if(a.status=="working")Pal.green else Pal.accent)}
  Info(a.role+" • "+(a.task?:"No active task"))
  Info(if(a.metricsAvailable)"CPU ${a.cpu}%   RAM ${a.memoryMb} MB" else "Resource metrics not connected")
  if(live) Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
   OutlinedButton(onClick={onControl(a,"pause")}){Text("Pause")}
   OutlinedButton(onClick={onControl(a,"resume")}){Text("Resume")}
   Button(onClick={onControl(a,"emergency_stop")},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF843637),contentColor=Color.White)){Text("Stop")}
  }
 }
}

@Composable private fun Home(s:Snapshot,go:(Tab)->Unit)=ListPage{
 item{Glass(Modifier.enter(0)){Text(s.host.name,color=Pal.text,fontWeight=FontWeight.Bold);Info("Monitoring ${s.host.monitoring} • Containment ${s.host.containment}");Info(if(s.mode=="live")"Resource metrics not yet connected" else "Demo metrics only")}}
 item{Row(Modifier.enter(1),horizontalArrangement=Arrangement.spacedBy(10.dp)){
  StatCard(Modifier.weight(1f),s.agents.count{it.status!="idle"},"Active agents",Pal.accent)
  StatCard(Modifier.weight(1f),if(s.approvalsAvailable)s.approvals.size else null,"Approvals",Pal.amber)
 }}
 item{TextButton(onClick={go(Tab.Approvals)},modifier=Modifier.enter(2)){Text(if(s.approvalsAvailable)"Open pending approvals" else "Approvals unavailable until native integration",color=Pal.accent)}}
 itemsIndexed(s.tasks){i,t->
  val on=LocalAnim.current
  val progress by animateFloatAsState(t.progress.coerceIn(0,100)/100f,motion(on,tween(900,easing=FastOutSlowInEasing)),label="progress")
  Glass(Modifier.enter(i+3)){
   Text(t.title,color=Pal.text,fontWeight=FontWeight.Bold);Info("${t.agentId?:"Unassigned"} • ${t.status}")
   LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth(),color=Pal.accent,trackColor=Pal.accent.copy(alpha=.15f))
  }
 }
 item{Info(if(s.mode=="live")"Recent recorded event sample: ${s.sampledEvents} (not full history)" else "Demo events: ${s.eventsToday}")}
}
@Composable private fun Agents(s:Snapshot,live:Boolean,onControl:(Agent,String)->Unit){
 var q by remember{mutableStateOf("")}
 ListPage{
  if(!live)item{Text("Native agent controls are not connected. Read-only monitoring is available.",color=Pal.amber,style=MaterialTheme.typography.bodySmall)}
  item{OutlinedTextField(q,{q=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Find agent")},colors=fieldColors())}
  itemsIndexed(s.agents.filter{it.name.contains(q,true)||it.task?.contains(q,true)==true}){i,a->AgentCard(i,a,live,onControl)}
 }
}
@Composable private fun Approvals(s:Snapshot,live:Boolean,onDecision:(Approval,Boolean)->Unit)=ListPage{
 if(!live)item{Text("Native approvals are not connected. Remote decisions are disabled.",color=Pal.amber,style=MaterialTheme.typography.bodySmall)}
 if(live&&s.approvals.isEmpty())item{Info("No pending approvals.")}
 itemsIndexed(s.approvals){i,a->Glass(Modifier.enter(i)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(a.title,color=Pal.text,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Tag(a.risk,if(a.risk=="critical")Pal.red else Pal.amber)}
  Info(a.detail);Text(a.resource,color=Pal.accent,style=MaterialTheme.typography.bodySmall)
  Info("${a.agentId} • ${a.type} • Expires: ${a.expiresAt}")
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedButton(onClick={onDecision(a,false)},enabled=live,modifier=Modifier.weight(1f)){Text("Deny")}
   Button(onClick={onDecision(a,true)},enabled=live,modifier=Modifier.weight(1f)){Text("Approve once")}
  }
 }}
}
@Composable private fun Files(s:Snapshot){
 var q by remember{mutableStateOf("")}
 var filter by remember{mutableStateOf("All")}
 ListPage{
  item{OutlinedTextField(q,{q=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Search path / agent")},colors=fieldColors())}
  item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("All","read","modified","created").forEach{f->FilterChip(selected=filter==f,onClick={filter=f},label={Text(f)},
   colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Pal.accent.copy(alpha=.2f),selectedLabelColor=Pal.accent))}}}
  itemsIndexed(s.files.filter{(filter=="All"||it.action==filter)&&(it.path.contains(q,true)||it.agentId.contains(q,true))}){i,f->Glass(Modifier.enter(i)){Tag(f.action);Text(f.path,color=Pal.text,fontWeight=FontWeight.Medium);Info("${f.agentId} • ${f.timestamp} • ${f.risk}")}}
 }
}
@Composable private fun History(s:Snapshot){
 var q by remember{mutableStateOf("")}
 ListPage{
  item{OutlinedTextField(q,{q=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Search events")},colors=fieldColors())}
  itemsIndexed(s.timeline.filter{it.title.contains(q,true)||it.detail.contains(q,true)||it.type.contains(q,true)}){i,e->Glass(Modifier.enter(i)){Tag(e.type);Text(e.title,color=Pal.text,fontWeight=FontWeight.Bold);Info(e.detail);Info("${e.timestamp} • ${e.agentId?:"System"}")}}
 }
}
