package com.phaize.companion

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private val pane=Color(0xFF132732)
private val quiet=Color(0xFF97B0BB)
private val teal=Color(0xFF69C9D6)
private val warning=Color(0xFFF1BD72)

@Composable private fun OpsCard(title:String,content:@Composable ColumnScope.()->Unit){
 Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=pane){
  Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
   content()
  }
 }
}
@Composable private fun OpsNote(text:String){Text(text,color=quiet,style=MaterialTheme.typography.bodySmall)}
@Composable private fun OpsMetric(label:String,value:String){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
  OpsNote(label);Text(value,color=teal,fontWeight=FontWeight.Medium)
 }
}
@Composable fun OperationsScreen(
 live:Boolean, busy:Boolean, page:OpsPage?, agents:List<Agent>,
 onReload:()->Unit,onAction:(String,JSONObject)->Unit
){
 var agentId by remember{mutableStateOf<String?>(null)}
 var cpu by remember{mutableStateOf("50")}
 var memory by remember{mutableStateOf("2048")}
 var network by remember{mutableStateOf("restricted")}
 var reason by remember{mutableStateOf("Emergency remote security lockdown")}
 var selectedAction by remember{mutableStateOf<Pair<String,JSONObject>?>(null)}
 fun propose(action:String,body:JSONObject){selectedAction=action to body}
 LaunchedEffect(agents){if(agentId==null||agents.none{it.id==agentId})agentId=agents.firstOrNull()?.id}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    Text("Remote operations",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    TextButton(onClick=onReload,enabled=live&&!busy){Text("Refresh")}
   }
  }
  if(!live)item{OpsCard("Connection required"){OpsNote("Controls remain disabled until the native desktop is online and independently authorizes this device.")}}
  else if(page==null)item{OpsCard("Native tools unavailable"){OpsNote("The desktop has not implemented the mobile operations bridge. These buttons will not issue commands without it.")}}
  item{OpsCard("Desktop health"){
   if(page?.health==null)OpsNote("No live system metrics available.")
   else {
    OpsMetric("CPU",page.health.cpuPercent.toString()+"%")
    OpsMetric("Memory",page.health.memoryPercent.toString()+"%")
    OpsMetric("Disk",page.health.diskPercent.toString()+"%")
    OpsMetric("Temperature",page.health.temperatureC?.let{it.toString()+" °C"}?:"Not reported")
    OpsMetric("Network",page.health.networkState)
    OpsMetric("Monitoring",page.health.coverage)
   }
  }}
  item{OpsCard("Security lockdown"){
   OpsNote("Request a native system-wide agent freeze. Existing MXC policies are never weakened. The host must confirm actual enforcement.")
   OutlinedTextField(reason,{reason=it.take(240)},label={Text("Reason for audit")},modifier=Modifier.fillMaxWidth())
   Button(onClick={propose("lockdown",JSONObject().put("reason",reason.trim()))},
    enabled=live&&page!=null&&!busy&&reason.trim().length>=8,
    colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF85393B))){Text("Lock down agents")}
  }}
  item{OpsCard("Agent resource policies"){
   OpsNote("Set maximum resource budgets. Changes are applied only by the native coordinator.")
   Row(Modifier.horizontalScroll(rememberScrollState())){
    agents.forEach{a->FilterChip(selected=agentId==a.id,onClick={agentId=a.id},label={Text(a.name)},modifier=Modifier.padding(end=6.dp))}
   }
   OutlinedTextField(cpu,{cpu=it.filter{c->c.isDigit()}.take(3)},label={Text("CPU maximum (10–100%)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   OutlinedTextField(memory,{memory=it.filter{c->c.isDigit()}.take(5)},label={Text("RAM maximum (256–65536 MB)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   Row(Modifier.horizontalScroll(rememberScrollState())){
    listOf("off","restricted","policy").forEach{v->FilterChip(selected=network==v,onClick={network=v},label={Text("Network: "+v)},modifier=Modifier.padding(end=6.dp))}
   }
   Button(onClick={propose("limits",JSONObject().put("agentId",agentId).put("cpuPercent",cpu.toInt()).put("memoryMb",memory.toInt()).put("network",network))},
    enabled=live&&page!=null&&!busy&&agentId!=null&&(cpu.toIntOrNull()?:0) in 10..100&&(memory.toIntOrNull()?:0) in 256..65536){Text("Apply limits")}
  }}
  item{OpsCard("Safe remote commands"){
   OpsNote("No arbitrary terminal access. These preset requests run only if desktop policy permits them in the selected workspace.")
   Row(Modifier.horizontalScroll(rememberScrollState())){
    agents.forEach{a->FilterChip(selected=agentId==a.id,onClick={agentId=a.id},label={Text(a.name)},modifier=Modifier.padding(end=6.dp))}
   }
   listOf("git_status" to "Git status","git_diff_stat" to "Diff summary","run_tests" to "Run project tests").forEach{(id,label)->
    OutlinedButton(onClick={propose("terminal_preset",JSONObject().put("agentId",agentId).put("commandId",id))},
     enabled=live&&page!=null&&!busy&&agentId!=null,modifier=Modifier.fillMaxWidth()){Text(label)}
   }
  }}
  item{OpsCard("Git project status"){
   val git=page?.git
   if(git==null)OpsNote("No Git status available.")
   else {
    OpsMetric("Branch",git.branch)
    OpsMetric("Modified files",git.dirtyFiles.toString())
    OpsMetric("Ahead / behind",""+git.ahead+" / "+git.behind)
    OpsNote("Latest: "+git.lastCommit)
   }
  }}
  item{OpsCard("Desktop capture"){
   OpsNote("Request a redacted capture of PhAize's own window. Captures are not automatically uploaded or exposed to arbitrary websites.")
   OutlinedButton(onClick={propose("capture",JSONObject().put("target","phaize_window"))},
    enabled=live&&page!=null&&!busy){Text("Request safe screenshot")}
   if(page?.captures?.isEmpty()==true)OpsNote("No captures listed.")
  }}
  if(page!=null)items(page.captures){c->OpsCard(c.description){
   OpsNote(c.timestamp+" • "+c.status)
   OpsNote("Capture reference: "+c.id+" (viewing pixels requires a separate audited image endpoint)")
  }}
  item{OpsCard("Verified checkpoints"){
   OpsNote("Restore requires native authorization and a second explicit confirmation. It must not silently overwrite work.")
   if(page?.checkpoints?.isEmpty()!=false)OpsNote("No verified checkpoints available.")
  }}
  if(page!=null)items(page.checkpoints){c->OpsCard(c.title){
   OpsNote(c.createdAt+" • "+c.description)
   OutlinedButton(onClick={propose("checkpoint_restore",JSONObject().put("checkpointId",c.id))},enabled=live&&!busy){Text("Request restore")}
  }}
  item{OpsCard("Recent command results"){
   OpsNote("Output must be sanitized and capped by the desktop before it reaches your phone.")
   if(page?.terminalHistory?.isEmpty()!=false)OpsNote("No command history.")
  }}
  if(page!=null)items(page.terminalHistory){c->OpsCard(c.label){
   OpsNote(c.agentId+" • "+c.status+" • "+c.timestamp)
   Text(c.output.take(1200),style=MaterialTheme.typography.bodySmall)
  }}
 }
 if(selectedAction!=null){
  val action=selectedAction!!
  AlertDialog(onDismissRequest={selectedAction=null},
   title={Text("Confirm "+action.first.replace('_',' '))},
   text={Text(when(action.first){
    "lockdown"->"This will request a security lockdown across agents. You must manually unlock at the desktop."
    "checkpoint_restore"->"This may overwrite project changes. The desktop must revalidate the checkpoint and show the impact before executing."
    "terminal_preset"->"Execute only the registered command preset inside the authorized sandbox?"
    "limits"->"Apply these restrictions to the selected agent?"
    else->"Request a redacted desktop capture? The desktop decides whether this device may access it."
   })},
   confirmButton={Button(enabled=live&&!busy,onClick={onAction(action.first,action.second);selectedAction=null}){Text("Confirm request")}},
   dismissButton={TextButton(onClick={selectedAction=null}){Text("Cancel")}}
  )
 }
}
