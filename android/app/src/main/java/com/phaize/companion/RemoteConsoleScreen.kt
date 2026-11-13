package com.phaize.companion

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val consoleSurface=Color(0xFF132732)
private val consoleHint=Color(0xFF9BB7C3)
private val consoleWarn=Color(0xFFEFBD72)
private val consoleDanger=Color(0xFFB84A54)

@Composable private fun RemoteCard(title:String,content:@Composable ColumnScope.()->Unit) {
 Surface(color=consoleSurface,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
   content()
  }
 }
}
@Composable private fun Note(value:String) {
 Text(value,color=consoleHint,style=MaterialTheme.typography.bodySmall)
}

/** Transport UI only: desktop must authorize and durably apply every command. */
@Composable fun RemoteConsoleScreen(
 live:Boolean,loading:Boolean,console:RemoteConsole?,
 onReload:()->Unit,
 onAction:(String,Map<String,Any>)->Unit
) {
 var confirmation by remember { mutableStateOf<Pair<String,Map<String,Any>>?>(null) }
 var cpu by remember { mutableIntStateOf(50) }
 var memory by remember { mutableIntStateOf(2048) }
 var network by remember { mutableStateOf("restricted") }
 var selected by remember { mutableStateOf<String?>(null) }
 val active=console?.resources?.find{it.agentId==selected}
 LaunchedEffect(active?.agentId,console?.revision){
  if(active!=null){cpu=active.cpuPercent.coerceIn(5,100);memory=active.memoryMb.coerceIn(256,32768);network=active.networkMode.takeIf{it in listOf("off","restricted","standard")} ?:"restricted"}
 }
 LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item {
   Row(verticalAlignment=Alignment.CenterVertically){
    Text("Remote command center",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
    TextButton(onClick=onReload,enabled=live&&!loading){Text("Refresh")}
   }
  }
  if(!live) item{RemoteCard("Disconnected"){Note("Connect PhAize desktop. All privileged controls are disabled while offline or in demo mode.")}}
  else if(loading) item {LinearProgressIndicator(modifier=Modifier.fillMaxWidth())}
  if(live&&console==null&&!loading)item{RemoteCard("Unavailable"){Note("The native desktop bridge does not yet provide the remote console endpoint.")}}
  if(live&&console!=null){
   item{RemoteCard("Emergency protection"){
    Text(if(console.locked)"Locked down" else "Desktop operating",color=if(console.locked)consoleWarn else MaterialTheme.colorScheme.primary)
    Note("Lockdown asks the native security coordinator to stop permitted sessions and restrict new execution. It does not bypass enforcement.")
    Button(onClick={confirmation="lockdown" to mapOf("reason" to "Manual remote security lockdown")},enabled=!console.locked&&!loading,colors=ButtonDefaults.buttonColors(containerColor=consoleDanger)){Text("Emergency lockdown")}
   }}
   item{RemoteCard("Git repository"){
    val git=console.git
    if(git==null)Note("Git status not exposed by the desktop.")
    else{
     Text(git.branch,fontWeight=FontWeight.Bold)
     Note((if(git.dirty)"Uncommitted changes" else "Working tree clean")+" • Ahead ${git.ahead} • Behind ${git.behind}")
     if(git.summary.isNotBlank())Note(git.summary)
    }
    OutlinedButton(onClick={confirmation="refresh_git" to emptyMap()},enabled=!loading){Text("Refresh Git status")}
   }}
   item{RemoteCard("Approved terminal commands"){
    Note("Only predefined commands supplied by the desktop can be requested. No arbitrary shell execution from the phone.")
    if(console.commands.isEmpty())Note("No approved commands offered.")
    console.commands.forEach{command->
     Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
      Column(modifier=Modifier.weight(1f)){
       Text(command.label,fontWeight=FontWeight.SemiBold)
       Note(command.description)
      }
      OutlinedButton(onClick={confirmation="run_command" to mapOf("commandId" to command.id)},enabled=!loading){Text("Run")}
     }
    }
   }}
   item{RemoteCard("Host preview"){
    Note("A capture request returns only redacted metadata in this version; image bytes are not exposed by the gateway.")
    val preview=console.preview
    if(preview!=null){Text(preview.status);Note(preview.capturedAt);Note(preview.description)}
    OutlinedButton(onClick={confirmation="capture_preview" to emptyMap()},enabled=!loading){Text("Request redacted preview")}
   }}
   item{RemoteCard("Agent resource limits"){
    Note("Requested limits can never exceed desktop policy or MXC isolation.")
    if(console.resources.isEmpty())Note("No agent resource profiles supplied by desktop.")
    Row(modifier=Modifier.horizontalScroll(rememberScrollState())){
     console.resources.forEach{agent->
      FilterChip(selected=selected==agent.agentId,onClick={selected=agent.agentId},
       label={Text(agent.agentName)},modifier=Modifier.padding(end=6.dp))
     }
    }
    if(active!=null){
     Text("CPU limit: $cpu%")
     Slider(value=cpu.toFloat(),onValueChange={cpu=it.toInt()},valueRange=5f..100f)
     Text("Memory limit: $memory MB")
     Slider(value=memory.toFloat(),onValueChange={memory=(it.toInt()/256)*256},valueRange=256f..16384f)
     Note("Network access")
     Row(modifier=Modifier.horizontalScroll(rememberScrollState())){
      listOf("off","restricted","standard").forEach{mode->FilterChip(selected=network==mode,onClick={network=mode},label={Text(mode)},modifier=Modifier.padding(end=6.dp))}
     }
     Button(onClick={confirmation="set_limits" to mapOf("agentId" to active.agentId,"cpuPercent" to cpu,"memoryMb" to memory,"networkMode" to network)},enabled=!loading){Text("Apply limits")}
    }
   }}
   item{RemoteCard("Restore checkpoint"){
    Note("Restoring can discard later changes. Native desktop validation and a separate audit record are mandatory.")
    if(console.checkpoints.isEmpty())Note("No restorable checkpoints reported.")
   }}
   items(console.checkpoints){cp->
    RemoteCard(cp.title){
     Note(cp.createdAt)
     Note(cp.detail)
     OutlinedButton(onClick={confirmation="restore_checkpoint" to mapOf("checkpointId" to cp.id)},enabled=!loading){Text("Request restore")}
    }
   }
  }
 }
 if(confirmation!=null){
  val (action,fields)=confirmation!!
  AlertDialog(
   onDismissRequest={confirmation=null},
   title={Text(when(action){"lockdown"->"Lock down desktop?";"restore_checkpoint"->"Restore checkpoint?";"set_limits"->"Change resource policy?";"run_command"->"Run approved command?";"capture_preview"->"Request preview?";else->"Refresh Git status?"})},
   text={Text("Send an authenticated request to PhAize desktop? The native coordinator must verify your device, the current revision, workspace permissions and audit requirements before applying it.")},
   confirmButton={Button(onClick={confirmation=null;onAction(action,fields)},enabled=live&&!loading){Text("Confirm request")}},
   dismissButton={TextButton(onClick={confirmation=null}){Text("Cancel")}}
  )
 }
}
