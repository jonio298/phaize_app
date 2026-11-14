package com.phaize.companion

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
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

private val quiet=Color(0xFF97B0BB)
private val teal=Color(0xFF69C9D6)
private val amber=Color(0xFFF1BD72)
private val crimson=Color(0xFFEA8B8B)
private val panel=Color(0xFF132732)
@Composable private fun PCard(title:String,content:@Composable ColumnScope.()->Unit){
 Surface(color=panel,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
   content()
  }
 }
}
@Composable private fun Muted(value:String){Text(value,color=quiet,style=MaterialTheme.typography.bodySmall)}

@Composable fun BriefingScreen(connected:Boolean,loading:Boolean,value:ProjectBriefing?,retry:()->Unit){
 LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    Text("Since you were away",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    TextButton(onClick=retry,enabled=connected&&!loading){Text("Refresh")}
   }
  }
  if(loading)item{LinearProgressIndicator(modifier=Modifier.fillMaxWidth())}
  if(!connected)item{PCard("Desktop required"){Muted("Connect to a trusted PhAize desktop to receive a real project briefing.")}}
  else if(value==null&&!loading)item{PCard("Briefing unavailable"){Muted("The native desktop does not yet provide briefing data. No summary is fabricated.")}}
  if(value!=null){
   item{PCard("Project overview"){
    if(value.workspace.isNotBlank())Muted(value.workspace)
    Text(if(value.summary.isBlank())"No written summary supplied by desktop." else value.summary)
    Muted("Files changed: ${value.filesChanged}")
    if(value.since.isNotBlank())Muted("Since: ${value.since}")
    if(value.generatedAt.isNotBlank())Muted("Updated: ${value.generatedAt}")
   }}
   item{PCard("Completed • ${value.completed.size}"){
    if(value.completed.isEmpty())Muted("No completed tasks reported.")
    value.completed.forEach{Text("✓  "+it)}
   }}
   item{PCard("Blockers • ${value.blockers.size}"){
    if(value.blockers.isEmpty())Muted("No blockers reported.")
    value.blockers.forEach{Text("!  "+it,color=amber)}
   }}
   item{PCard("Decisions needed • ${value.decisions.size}"){
    if(value.decisions.isEmpty())Muted("No outstanding decisions reported.")
    value.decisions.forEach{Text("•  "+it)}
   }}
  }
 }
}

@Composable fun BuildMonitorScreen(connected:Boolean,loading:Boolean,value:BuildFeed?,refresh:()->Unit){
 val ctx=LocalContext.current
 var filter by remember{mutableStateOf("All")}
 LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    Text("Build & CI center",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    TextButton(onClick=refresh,enabled=connected&&!loading){Text("Refresh")}
   }
  }
  item{PCard("Trusted build status"){
   Muted("Build results come from the paired desktop's configured repository. PhAize cannot run arbitrary GitHub jobs from this screen.")
   if(value?.repository?.isNotBlank()==true)Text(value.repository)
   if(value?.generatedAt?.isNotBlank()==true)Muted("Updated: "+value.generatedAt)
  }}
  if(loading)item{LinearProgressIndicator(modifier=Modifier.fillMaxWidth())}
  if(!connected)item{Muted("Connect to desktop to see CI results.")}
  else if(value==null&&!loading)item{Muted("Build feed is not available from the desktop bridge.")}
  if(value!=null){
   item{Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
    listOf("All","failed","passed","running").forEach{label->
     FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)})
    }
   }}
   val runs=value.runs.filter{run->when(filter){
    "failed"->run.conclusion in listOf("failure","timed_out","cancelled")
    "passed"->run.conclusion=="success"
    "running"->run.status in listOf("queued","in_progress","waiting")
    else->true
   }}
   if(runs.isEmpty())item{Muted("No matching build runs reported.")}
   items(runs){run->PCard(run.title.ifBlank { run.workflow.ifBlank{"Build"} }){
    val failed=run.conclusion in listOf("failure","timed_out")
    Text(run.conclusion.ifBlank {run.status}.uppercase(),color=if(failed)crimson else if(run.conclusion=="success")teal else amber)
    Muted(listOf(run.branch,run.workflow,run.updatedAt).filter{it.isNotBlank()}.joinToString(" • "))
    val parsed=runCatching{Uri.parse(run.url)}.getOrNull()
    if(parsed!=null&&parsed.scheme=="https"&&parsed.host=="github.com"){
     TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,parsed))}){Text("Open GitHub run")}
    }
   }}
  }
 }
}
