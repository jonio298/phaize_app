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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val ink=Color(0xFFEAF5F7)
private val subdued=Color(0xFF9BB7C3)
private val surface=Color(0xFF132732)
private val cyan=Color(0xFF69C9D6)
private val warning=Color(0xFFF1BD72)
private val danger=Color(0xFFEA8B8B)

@Composable private fun Section(title:String,body:@Composable ColumnScope.()->Unit){
 Surface(modifier=Modifier.fillMaxWidth(),color=surface,shape=RoundedCornerShape(18.dp)){
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(title,color=ink,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
   body()
  }
 }
}
@Composable private fun Hint(value:String){Text(value,color=subdued,style=MaterialTheme.typography.bodySmall)}
@Composable private fun Chip(label:String,selected:Boolean,onClick:()->Unit){
 FilterChip(selected=selected,onClick=onClick,label={Text(label)},modifier=Modifier.padding(end=6.dp))
}
@Composable private fun Container(title:String,body:androidx.compose.foundation.lazy.LazyListScope.()->Unit){
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{Text(title,color=ink,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
  body()
 }
}

@Composable fun TasksScreen(s:Snapshot,live:Boolean,onCreate:(String,String,String?,String)->Unit){
 var open by remember{mutableStateOf(false)}
 var title by remember{mutableStateOf("")}
 var instructions by remember{mutableStateOf("")}
 var assignee by remember{mutableStateOf<String?>(null)}
 var priority by remember{mutableStateOf("normal")}
 Container("Tasks & assignments"){
  item{Section("Task queue"){
   Hint("Create an objective, choose an agent or let desktop PhAize delegate it.")
   Button(onClick={open=true},enabled=live){Text("+ Add task")}
   if(!live)Hint("Connect to the desktop to submit tasks.")
  }}
  if(s.tasks.isEmpty())item{Section("No tasks"){Hint("No tasks have been reported.")}}
  items(s.tasks){task->Section(task.title){
   Hint("Agent: "+(s.agents.find{it.id==task.agentId}?.name?:"Unassigned")+"  •  "+task.status)
   LinearProgressIndicator(progress={task.progress.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth())
   Hint(task.progress.toString()+"% complete")
  }}
 }
 if(open)AlertDialog(
  onDismissRequest={open=false},title={Text("New desktop task")},
  text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
   OutlinedTextField(title,{title=it.take(160)},label={Text("Title")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   OutlinedTextField(instructions,{instructions=it.take(4000)},label={Text("Instructions and expected result")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=5)
   Text("Assign to",fontWeight=FontWeight.Bold)
   Row(Modifier.horizontalScroll(rememberScrollState())){
    Chip("Auto",assignee==null){assignee=null}
    s.agents.forEach{agent->Chip(agent.name,assignee==agent.id){assignee=agent.id}}
   }
   Text("Priority",fontWeight=FontWeight.Bold)
   Row{listOf("low","normal","high").forEach{p->Chip(p,priority==p){priority=p}}}
   Hint("The desktop validates task scope, permissions and scheduling before accepting.")
  }},
  confirmButton={Button(onClick={
   onCreate(title.trim(),instructions,assignee,priority)
   open=false;title="";instructions=""
  },enabled=live&&title.trim().length>=3){Text("Send task")}},
  dismissButton={TextButton(onClick={open=false}){Text("Cancel")}}
 )
}
@Composable fun FileCenterScreen(
 s:Snapshot,live:Boolean,page:BrowserPage?,stack:List<String>,loading:Boolean,
 onOpen:(String)->Unit,onBack:()->Unit
){
 var mode by remember{mutableStateOf("Browse")}
 var query by remember{mutableStateOf("")}
 Container("Files & records"){
  item{Row(Modifier.horizontalScroll(rememberScrollState())){
   Chip("Workspace browser",mode=="Browse"){mode="Browse";query=""}
   Chip("File activity",mode=="Activity"){mode="Activity";query=""}
  }}
  if(mode=="Browse"){
   item{Section("Authorized workspace"){
    Hint("Read-only directory metadata. The desktop decides scope and redacts sensitive names.")
    Row(verticalAlignment=Alignment.CenterVertically){
     if(stack.isNotEmpty())TextButton(onClick=onBack){Text("Back")}
     Hint(if(stack.isEmpty())"Workspace root" else "Folder level "+stack.size)
    }
   }}
   if(!live)item{Hint("Connect a live desktop to browse.")}
   else {
    item{OutlinedTextField(query,{query=it},label={Text("Filter files and folders")},singleLine=true,modifier=Modifier.fillMaxWidth())}
    if(loading)item{LinearProgressIndicator(modifier=Modifier.fillMaxWidth())}
    if(page==null)item{Hint("No listing available. Native bridge support is required.")}
    else {
     if(page.entries.isEmpty())item{Hint("This folder is empty.")}
     items(page.entries.filter{it.name.contains(query,true)}){entry->
      Section(entry.name){
       Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
        Column(Modifier.weight(1f)){
         Hint(if(entry.kind=="directory")"Folder" else "File • "+entry.sizeBytes+" bytes")
         if(entry.modifiedAt.isNotBlank())Hint(entry.modifiedAt)
         if(entry.risk.isNotBlank())Hint("Risk: "+entry.risk)
        }
        if(entry.kind=="directory")TextButton(onClick={onOpen(entry.id)}){Text("Open")}
       }
      }
     }
     if(page.truncated)item{Hint("Listing truncated at 200 items.")}
    }
   }
  }else{
   item{OutlinedTextField(query,{query=it},label={Text("Search file records")},modifier=Modifier.fillMaxWidth())}
   if(s.files.isEmpty())item{Hint("No file events recorded.")}
   items(s.files.filter{it.path.contains(query,true)||it.agentId.contains(query,true)||it.action.contains(query,true)}){file->
    Section(file.path){
     Hint(file.action+" • "+file.agentId+" • "+file.risk)
     Hint(file.timestamp)
    }
   }
  }
 }
}
@Composable fun LogCenterScreen(s:Snapshot,live:Boolean,page:ErrorPage?,loading:Boolean){
 var severity by remember{mutableStateOf("All")}
 var query by remember{mutableStateOf("")}
 val records=page?.entries?:emptyList()
 Container("Errors & diagnostics"){
  item{Section("Desktop status"){
   Hint("Monitoring: "+s.host.monitoring+"  •  Containment: "+s.host.containment)
   Hint("Diagnostic logs are read-only and must be redacted on the desktop.")
  }}
  item{OutlinedTextField(query,{query=it},label={Text("Search errors or components")},modifier=Modifier.fillMaxWidth())}
  item{Row(Modifier.horizontalScroll(rememberScrollState())){
   listOf("All","critical","error","warning","info").forEach{v->Chip(v,severity==v){severity=v}}
  }}
  if(!live)item{Hint("Connect to a desktop to retrieve error logs.")}
  else if(loading)item{LinearProgressIndicator(modifier=Modifier.fillMaxWidth())}
  else if(page==null)item{Hint("The native desktop error endpoint is not available.")}
  else if(records.isEmpty())item{Hint("No diagnostic entries.")}
  items(records.filter{(severity=="All"||it.severity.equals(severity,true))&&(it.source.contains(query,true)||it.message.contains(query,true))}){e->
   Section(e.source){
    Text(e.severity.uppercase(),color=if(e.severity in listOf("error","critical"))danger else warning,style=MaterialTheme.typography.labelSmall)
    Text(e.message,color=ink)
    Hint(e.timestamp+" • "+(e.agentId?:"System"))
   }
  }
  if(page?.nextCursor!=null)item{Hint("More logs are available through paginated desktop queries.")}
 }
}
