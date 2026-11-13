package com.phaize.companion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat=DateTimeFormatter.ofPattern("MMM d, HH:mm")
/** Shows an ISO timestamp in the phone's time zone; anything else (such as "sample") is shown as it came. */
fun shortTime(ts:String):String=try{timeFormat.format(Instant.parse(ts).atZone(ZoneId.systemDefault()))}catch(_:Exception){ts}

/** Splits a message into plain text and fenced code, in order. */
fun splitFences(text:String):List<Pair<Boolean,String>>{
 val out=ArrayList<Pair<Boolean,String>>()
 val parts=text.split("```")
 parts.forEachIndexed{i,part->
  if(i%2==1){
   // A leading language tag such as "js" sits on the first line of the fence.
   val body=part.substringAfter('\n',part).trim('\n')
   if(body.isNotEmpty())out.add(true to body)
  }else if(part.isNotBlank())out.add(false to part.trim('\n'))
 }
 return out
}

private fun agentColour(agentId:String):Color=when(agentId){"codex"->Color(0xFF7FB6FF);"claude"->Color(0xFFE8A27A);else->Pal.accent}

@Composable private fun Avatar(label:String,colour:Color){
 Box(Modifier.size(40.dp).clip(CircleShape).background(colour.copy(alpha=.16f)).border(1.dp,colour.copy(alpha=.7f),CircleShape),contentAlignment=Alignment.Center){
  Text(label.take(1).uppercase(),color=colour,fontWeight=FontWeight.Bold)
 }
}

@Composable private fun ChatRow(index:Int,c:ChatSummary,onOpen:()->Unit){
 val colour=agentColour(c.agentId)
 Glass(Modifier.enter(index).pressable(onOpen)){
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
   Avatar(c.title,colour)
   Column(Modifier.weight(1f)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
     Text(c.title,color=Pal.text,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
     Info(shortTime(c.updatedAt))
    }
    Text(c.preview,color=Pal.muted,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
    Info("${c.messageCount} message${if(c.messageCount==1)"" else "s"}")
   }
   Icon(Icons.Outlined.ChevronRight,null,tint=Pal.accent)
  }
 }
}

@Composable private fun CodeBlock(code:String){
 val shape=RoundedCornerShape(12.dp)
 Box(Modifier.fillMaxWidth().clip(shape).background(Color.Black.copy(alpha=.38f)).border(1.dp,Pal.border,shape).horizontalScroll(rememberScrollState()).padding(10.dp)){
  Text(code,color=Pal.text,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
 }
}

@Composable private fun Bubble(index:Int,m:ChatMessage){
 if(m.role=="system"){
  Box(Modifier.fillMaxWidth().enter(index),contentAlignment=Alignment.Center){
   Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Pal.panel).border(1.dp,Pal.border.copy(alpha=.5f),RoundedCornerShape(12.dp)).padding(horizontal=12.dp,vertical=6.dp)){
    Text(m.text,color=Pal.muted,style=MaterialTheme.typography.labelMedium)
   }
  }
  return
 }
 val mine=m.role=="user"
 val shape=RoundedCornerShape(topStart=18.dp,topEnd=18.dp,bottomStart=if(mine)18.dp else 4.dp,bottomEnd=if(mine)4.dp else 18.dp)
 val fill=if(mine)Brush.verticalGradient(listOf(Pal.accent.copy(alpha=.26f),Pal.accent.copy(alpha=.12f))) else Brush.verticalGradient(listOf(Pal.panelHi,Pal.panel))
 Row(Modifier.fillMaxWidth().enter(index),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){
  Column(Modifier.widthIn(max=330.dp),horizontalAlignment=if(mine)Alignment.End else Alignment.Start){
   Text(m.speaker.ifBlank{if(mine)"You" else "Agent"},color=if(mine)Pal.accent else Pal.muted,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=6.dp,vertical=2.dp))
   Column(Modifier.clip(shape).background(fill).border(1.dp,if(mine)Pal.accent.copy(alpha=.55f) else Pal.border,shape).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    SelectionContainer{
     Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
      splitFences(m.text).forEach{(code,body)->
       if(code)CodeBlock(body) else Text(body,color=Pal.text,style=MaterialTheme.typography.bodyMedium)
      }
     }
    }
   }
   Info(shortTime(m.timestamp))
  }
 }
}

@Composable private fun Conversation(chat:ChatSummary,messages:List<ChatMessage>,loading:Boolean,onBack:()->Unit){
 BackHandler(onBack=onBack)
 val state=rememberLazyListState()
 LaunchedEffect(messages.size){if(messages.isNotEmpty())state.animateScrollToItem(messages.size)}
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back to chats",tint=Pal.accent)}
   Avatar(chat.title,agentColour(chat.agentId))
   Column(Modifier.padding(start=10.dp)){
    Text(chat.title,color=Pal.text,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
    Info(if(loading)"Updating…" else "Read only")
   }
  }
  LazyColumn(Modifier.fillMaxSize(),state=state,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   itemsIndexed(messages,key={i,m->m.id.ifBlank{"i$i"}+i}){i,m->Bubble(i-(messages.size-8).coerceAtLeast(0),m)}
   item{Info("You are viewing this conversation. Replies and approvals are not possible from here.")}
  }
 }
}

/** The chats tab: a list of conversations, then a read-only conversation view that slides in. */
@Composable fun ChatsPage(banner:String?,chats:List<ChatSummary>,open:ChatSummary?,messages:List<ChatMessage>,loading:Boolean,onOpen:(ChatSummary)->Unit,onBack:()->Unit){
 val on=LocalAnim.current
 AnimatedContent(targetState=open,transitionSpec={
  if(!on)EnterTransition.None togetherWith ExitTransition.None
  else if(targetState!=null)(fadeIn(tween(240))+slideInHorizontally(tween(300,easing=FastOutSlowInEasing)){it/6}) togetherWith (fadeOut(tween(140))+slideOutHorizontally(tween(260)){-it/8})
  else (fadeIn(tween(240))+slideInHorizontally(tween(300,easing=FastOutSlowInEasing)){-it/6}) togetherWith (fadeOut(tween(140))+slideOutHorizontally(tween(260)){it/8})
 },label="chat"){current->
  if(current!=null)Conversation(current,messages,loading,onBack)
  else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
   if(banner!=null)item{Glass(Modifier.enter(0)){Text(banner,color=Pal.amber,style=MaterialTheme.typography.bodySmall)}}
   if(chats.isEmpty())item{Info("No conversations yet.")}
   itemsIndexed(chats,key={_,c->c.id}){i,c->ChatRow(i+1,c){onOpen(c)}}
  }
 }
}

/** Sample conversations so the tab is useful before a gateway is connected. Never mixed with live data. */
fun demoChats():ChatList=ChatList(false,listOf(
 ChatSummary("chat-codex","Android gateway integration","codex","Understood. Chats are read only and approvals stay on their own screen.","sample",4),
 ChatSummary("chat-claude","Security review of the bridge","claude","Peer approved, not verified. Native checks are still pending.","sample",3)))
fun demoMessages(id:String):List<ChatMessage> = when(id){
 "chat-codex"->listOf(
  ChatMessage("1","user","You","Add chat viewing to the mobile app. Read only for now.","sample"),
  ChatMessage("2","agent","Codex","On it. I will add two read-only routes to the gateway, then this screen.","sample"),
  ChatMessage("3","agent","Codex","Routes:\n```\nGET /v1/chats\nGET /v1/chats/:id\n```\nNext is the Android screen.","sample"),
  ChatMessage("4","system","PhAize","Task T-01 assigned to Codex. This grants no permissions.","sample"))
 else->listOf(
  ChatMessage("1","agent","Claude Code","Reviewing the bridge contract against the gateway.","sample"),
  ChatMessage("2","agent","Claude Code","A phone decision must be re-checked by the desktop for revision and expiry. Confirmed.","sample"),
  ChatMessage("3","system","PhAize","Peer approved, not verified. Native checks are still pending.","sample"))
}
