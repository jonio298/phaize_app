package com.phaize.companion

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val backgroundCard=Color(0xFF132732)
private val subduedText=Color(0xFF9BB7C3)
private val alertRed=Color(0xFFEA8B8B)
private val alertAmber=Color(0xFFF1BD72)
private val accentCyan=Color(0xFF69C9D6)
private val successGreen=Color(0xFF82DAAE)

@Composable private fun Panel(title:String,body:@Composable ColumnScope.()->Unit){
 Surface(color=backgroundCard,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
  Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
   body()
  }
 }
}
@Composable private fun Note(text:String){Text(text,color=subduedText,style=MaterialTheme.typography.bodySmall)}
@Composable private fun PaneList(title:String,body:androidx.compose.foundation.lazy.LazyListScope.()->Unit){
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
  item{Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
  body()
 }
}
@Composable private fun Choice(values:List<String>,chosen:String,onPick:(String)->Unit){
 Row(Modifier.horizontalScroll(rememberScrollState())){
  values.forEach{v->FilterChip(selected=chosen==v,onClick={onPick(v)},label={Text(v)},modifier=Modifier.padding(end=6.dp))}
 }
}

@Composable fun GoalScreen(live:Boolean,busy:Boolean,onSend:(String,String,String)->Unit){
 var goal by remember{mutableStateOf("")}
 var instructions by remember{mutableStateOf("")}
 var priority by remember{mutableStateOf("normal")}
 PaneList("Goal delegator"){
  item{Panel("Delegate a development objective"){
   Note("The desktop coordinator plans subtasks, selects permitted agents, and manages dependencies. Nothing executes on the phone.")
   OutlinedTextField(goal,{goal=it.take(240)},label={Text("What should your team accomplish?")},modifier=Modifier.fillMaxWidth(),minLines=2)
   OutlinedTextField(instructions,{instructions=it.take(4000)},label={Text("Constraints and acceptance criteria")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=7)
   Text("Priority",fontWeight=FontWeight.Medium)
   Choice(listOf("low","normal","high"),priority){priority=it}
   Button(onClick={onSend(goal.trim(),instructions,priority);goal="";instructions=""},enabled=live&&!busy&&goal.trim().length>=3){Text("Delegate goal")}
   if(!live)Note("Requires the live PhAize desktop task coordinator.")
  }}
  item{Panel("Security boundaries"){
   Note("Delegation never grants write/delete privileges, changes workspace scope, or bypasses approval prompts. Desktop policy remains authoritative.")
  }}
 }
}
@Composable fun AgentChatScreen(
 agents:List<Agent>,selectedId:String?,live:Boolean,loading:Boolean,page:ChatPage?,
 onSelect:(String)->Unit,onSend:(String,String)->Unit
){
 var message by remember{mutableStateOf("")}
 val selected=agents.find{it.id==selectedId}
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=6.dp)){
   agents.forEach{a->FilterChip(selected=a.id==selectedId,onClick={onSelect(a.id)},label={Text(a.name)},modifier=Modifier.padding(end=6.dp))}
  }
  if(!live)Note("Connect to your desktop for real agent conversations.")
  if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
  LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   if(selected==null)item{Panel("Select an agent"){Note("Choose Codex or Claude to view that agent's conversation.")}}
   else if(page==null)item{Panel("Conversation unavailable"){Note("The desktop chat bridge has not supplied a transcript.")}}
   else if(page.messages.isEmpty())item{Panel("No conversation yet"){Note("Messages will appear when the native desktop session is connected.")}}
   if(page!=null)items(page.messages){m->Panel(m.sender+" • "+m.timestamp){
    Text(m.text,style=MaterialTheme.typography.bodyMedium)
   }}
  }
  Row(Modifier.padding(10.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedTextField(message,{message=it.take(4000)},label={Text("Message agent")},modifier=Modifier.weight(1f),maxLines=4)
   Button(onClick={selectedId?.let{onSend(it,message.trim())};message=""},enabled=live&&selected!=null&&message.isNotBlank()&&!loading){Text("Send")}
  }
  Note("Messages are requests; agent execution remains subject to desktop containment.")
 }
}
@Composable fun CodeReviewScreen(live:Boolean,loading:Boolean,page:ReviewPage?,onDecision:(ReviewItem,String)->Unit){
 var selected by remember{mutableStateOf<ReviewItem?>(null)}
 var pendingDecision by remember{mutableStateOf<Pair<ReviewItem,String>?>(null)}
 PaneList("Code review"){
  item{Panel("Review queue"){
   Note("View native proposed patches, review changes, and send an audited decision. This does not automatically bypass file permissions or merge code.")
   if(!live)Note("Connect to your desktop to review changes.")
   if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
  }}
  if(live&&page?.reviews?.isEmpty()==true)item{Note("No code reviews pending.")}
  if(page!=null)items(page.reviews){review->Panel(review.title){
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Text(review.status,color=accentCyan,style=MaterialTheme.typography.labelMedium)
    Note("Agent: "+review.agentId)
   }
   Note(review.summary)
   Text("${review.files.size} file(s)")
   OutlinedButton(onClick={selected=review}){Text("Inspect diff")}
  }}
 }
 if(selected!=null){
  val review=selected!!
  AlertDialog(onDismissRequest={selected=null},title={Text(review.title)},
   text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Note("Revision "+review.revision+" • "+review.summary)
    review.files.forEach{f->Panel(f.path){
     Text("+${f.additions}  -${f.deletions}",color=successGreen)
     Text(f.patch.take(12000),style=MaterialTheme.typography.bodySmall)
    }}
    Note("Review acceptance is advisory. Actual patch application still requires separate native authorization.")
   }},
   confirmButton={Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
    TextButton(onClick={pendingDecision=review to "request_changes";selected=null}){Text("Request changes")}
    Button(onClick={pendingDecision=review to "approve_review";selected=null}){Text("Accept review")}
   }},
   dismissButton={TextButton(onClick={selected=null}){Text("Close")}})
  }
 if(choice!=null){
  val decision=choice!!
  val review=page?.reviews?.find{it.id==selected?.id}
  // The review ID is captured by the pending decision state below, never inferred from a replacement list.
 }
}
@Composable fun AlertsScreen(live:Boolean,loading:Boolean,page:AlertPage?,onOpenApprovals:()->Unit){
 var filter by remember{mutableStateOf("All")}
 PaneList("Security alerts"){
  item{Panel("Alert center"){
   Note("Alerts come from native PhAize monitoring. Critical failures cannot silently turn into successful approvals.")
   OutlinedButton(onClick=onOpenApprovals,enabled=live){Text("Open approval queue")}
   Choice(listOf("All","critical","high","warning","info"),filter){filter=it}
   if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(!live)Note("Connect to your desktop to retrieve live alerts.")
  }}
  if(page?.alerts?.isEmpty()==true)item{Note("No alerts were returned by the desktop.")}
  if(page!=null)items(page.alerts.filter{filter=="All"||it.severity.equals(filter,true)}){a->
   Panel(a.title){
    val color=when(a.severity.lowercase()){"critical","high"->alertRed;"warning"->alertAmber;else->accentCyan}
    Text(a.severity.uppercase(),color=color,style=MaterialTheme.typography.labelSmall)
    Text(a.message)
    Note(a.timestamp+" • "+(a.agentId?:"System"))
   }
  }
 }
}
