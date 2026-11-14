package com.phaize.companion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun taskColour(status:String):Color=when(status){"completed"->Pal.green;"blocked","failed"->Pal.red;"revision","review"->Pal.amber;else->Pal.accent}
private fun taskLabel(status:String):String=when(status){"in_progress"->"In progress";"completed"->"Done";else->status.replace('_',' ').replaceFirstChar{it.uppercase()}}
private val needsYou=setOf("blocked","failed","review","revision")

/** A row of section choices (scrolls sideways when there are many) with the chosen section's page below it. */
@Composable fun Segments(options:List<String>,chosen:String,onChoose:(String)->Unit,content:@Composable ()->Unit){
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start=16.dp,end=16.dp,top=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   options.forEach{f->FilterChip(selected=chosen==f,onClick={onChoose(f)},label={Text(f)},
    colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Pal.accent.copy(alpha=.2f),selectedLabelColor=Pal.accent))}
  }
  Box(Modifier.weight(1f)){content()}
 }
}

/** Shown above every screen that could change something on the desktop: remote actions stay off until the independent security review. */
@Composable fun ControlsOff(content:@Composable ()->Unit){
 Column(Modifier.fillMaxSize()){
  Glass(Modifier.padding(start=16.dp,end=16.dp,top=8.dp)){
   Text("Remote actions are turned off",color=Pal.amber,fontWeight=FontWeight.Bold)
   Info("This phone can look but not change anything. Approving, sending, assigning and controlling agents stay disabled until the security review is done.")
  }
  Box(Modifier.weight(1f)){content()}
 }
}

@Composable private fun Chips(options:List<String>,chosen:String,onChoose:(String)->Unit){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
  options.forEach{f->FilterChip(selected=chosen==f,onClick={onChoose(f)},label={Text(f)},
   colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Pal.accent.copy(alpha=.2f),selectedLabelColor=Pal.accent))}
 }
}

/** The task list, read only: stage, who has it, how far along, and why it is stuck when it is. */
@Composable fun TasksPage(s:Snapshot){
 var filter by remember{mutableStateOf("All")}
 val shown=s.tasks.filter{when(filter){"Active"->it.status=="in_progress"||it.status=="pending";"Needs you"->it.status in needsYou;"Done"->it.status=="completed";else->true}}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
  if(!s.tasksAvailable)item{Glass(Modifier.enter(0)){Text("No tasks shared",color=Pal.text,fontWeight=FontWeight.Bold);Info("Pair with your PC and turn on Share tasks under Settings > Mobile & Network on the desktop.")}}
  else{
   item{Chips(listOf("All","Active","Needs you","Done"),filter){filter=it}}
   if(shown.isEmpty())item{Info(if(s.tasks.isEmpty())"There are no tasks yet." else "No tasks in this view.")}
  }
  itemsIndexed(shown,key={_,t->t.id}){i,t->
   val on=LocalAnim.current
   val progress by animateFloatAsState(t.progress.coerceIn(0,100)/100f,motion(on,tween(800,easing=FastOutSlowInEasing)),label="taskProgress")
   Glass(Modifier.enter(i)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
     Text(t.title,color=Pal.text,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f).padding(end=8.dp))
     Tag(taskLabel(t.status),taskColour(t.status))
    }
    Info(t.agentId?:"Unassigned")
    LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth(),color=taskColour(t.status),trackColor=Pal.accent.copy(alpha=.15f))
    t.detail?.let{Text(it,color=if(t.status=="blocked"||t.status=="failed")Pal.red else Pal.amber,style=MaterialTheme.typography.bodySmall)}
   }
  }
 }
}

/** The log: files touched, the event timeline, the changelog and the handoffs between agents. */
@Composable fun LogPage(s:Snapshot,docs:DocsView,files:@Composable (Snapshot)->Unit,timeline:@Composable (Snapshot)->Unit){
 var segment by remember{mutableStateOf("Timeline")}
 Column(Modifier.fillMaxSize()){
  Box(Modifier.padding(start=16.dp,end=16.dp,top=8.dp)){Chips(listOf("Timeline","Files","Changelog","Handoffs"),segment){segment=it}}
  Box(Modifier.weight(1f)){
   when(segment){
    "Files"->files(s)
    "Changelog"->DocList(docs.available,docs.changelog,"No changelog entries yet.")
    "Handoffs"->DocList(docs.available,docs.handoffs,"No handoffs between agents yet.")
    else->timeline(s)
   }
  }
 }
}

@Composable private fun DocList(available:Boolean,entries:List<DocEntry>,empty:String){
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  if(!available)item{Info("The desktop is not sharing the changelog and handoffs. Turn on Share changelog and handoffs under Settings > Mobile & Network on the desktop.")}
  else if(entries.isEmpty())item{Info(empty)}
  itemsIndexed(entries,key={i,e->e.id+i}){i,e->
   Glass(Modifier.enter(i)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Tag(e.who.ifBlank{"PhAIze"});Info(shortTime(e.at))}
    Text(e.text,color=Pal.text,style=MaterialTheme.typography.bodyMedium)
   }
  }
 }
}
