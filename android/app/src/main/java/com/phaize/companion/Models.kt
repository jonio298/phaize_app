package com.phaize.companion

import org.json.JSONArray
import org.json.JSONObject

data class Agent(val id:String,val name:String,val status:String,val role:String,val task:String?,val cpu:Int,val memoryMb:Int,val readPermission:String,val writePermission:String,val deletePermission:String)
data class WorkTask(val id:String,val title:String,val agentId:String?,val status:String,val progress:Int)
data class Approval(val id:String,val revision:Int,val agentId:String,val title:String,val detail:String,val resource:String,val type:String,val risk:String,val expiresAt:String)
data class FileEvent(val id:String,val path:String,val action:String,val agentId:String,val timestamp:String,val risk:String)
data class BrowserEntry(val id:String,val name:String,val kind:String,val sizeBytes:Long,val modifiedAt:String,val risk:String)
data class BrowserPage(val parentId:String?,val entries:List<BrowserEntry>,val truncated:Boolean)
data class ErrorEntry(val id:String,val severity:String,val source:String,val message:String,val timestamp:String,val agentId:String?)
data class ErrorPage(val entries:List<ErrorEntry>,val nextCursor:String?)
data class Activity(val id:String,val type:String,val title:String,val detail:String,val timestamp:String,val agentId:String?)
data class Host(val name:String,val online:Boolean,val monitoring:String,val containment:String,val cpu:Int,val memoryPercent:Int)
data class Snapshot(val revision:Int,val mode:String,val host:Host,val agents:List<Agent>,val tasks:List<WorkTask>,val approvals:List<Approval>,val files:List<FileEvent>,val timeline:List<Activity>,val eventsToday:Int,val blockedToday:Int,val completedToday:Int)
private fun <T> JSONObject.items(name:String,make:(JSONObject)->T):List<T>{val a=optJSONArray(name)?:JSONArray();return (0 until a.length()).mapNotNull{a.optJSONObject(it)?.let(make)}}
private fun JSONObject.s(k:String)=optString(k,"")
private fun JSONObject.n(k:String)=if(isNull(k))null else optString(k).takeIf{it.isNotEmpty()}
fun parseSnapshot(text:String):Snapshot {
 val o=JSONObject(text);val h=o.getJSONObject("host");val stats=o.optJSONObject("statistics")?:JSONObject()
 return Snapshot(o.optInt("revision"),o.s("mode"),Host(h.s("name"),h.optBoolean("online"),h.s("monitoring"),h.s("containment"),h.optInt("cpu"),h.optInt("memoryPercent")),
 o.items("agents"){Agent(it.s("id"),it.s("name"),it.s("status"),it.s("role"),it.n("task"),it.optInt("cpu"),it.optInt("memoryMb"),it.optJSONObject("permissions")?.optString("read","allow")?:"allow",it.optJSONObject("permissions")?.optString("write","ask")?:"ask",it.optJSONObject("permissions")?.optString("delete","ask")?:"ask")},
 o.items("tasks"){WorkTask(it.s("id"),it.s("title"),it.n("agentId"),it.s("status"),it.optInt("progress"))},
 o.items("approvals"){Approval(it.s("id"),it.optInt("revision"),it.s("agentId"),it.s("title"),it.s("detail"),it.s("resource"),it.s("type"),it.s("risk"),it.s("expiresAt"))},
 o.items("files"){FileEvent(it.s("id"),it.s("path"),it.s("action"),it.s("agentId"),it.s("timestamp"),it.s("risk"))},
 o.items("timeline"){Activity(it.s("id"),it.s("type"),it.s("title"),it.s("detail"),it.s("timestamp"),it.n("agentId"))},
 stats.optInt("eventsToday"),stats.optInt("blockedToday"),stats.optInt("completedToday"))
}

fun parseBrowserPage(text:String):BrowserPage {
 val o=JSONObject(text)
 return BrowserPage(o.optString("parentId").takeIf{it.isNotEmpty()},o.items("entries"){
  BrowserEntry(it.s("id"),it.s("name"),it.s("kind"),it.optLong("sizeBytes"),it.s("modifiedAt"),it.s("risk"))
 },o.optBoolean("truncated"))
}
fun parseErrorPage(text:String):ErrorPage {
 val o=JSONObject(text)
 return ErrorPage(o.items("entries"){
  ErrorEntry(it.s("id"),it.s("severity"),it.s("source"),it.s("message"),it.s("timestamp"),it.n("agentId"))
 },o.optString("nextCursor").takeIf{it.isNotEmpty()})
}

data class ChatMessage(val id:String,val agentId:String,val sender:String,val text:String,val timestamp:String)
data class ChatPage(val messages:List<ChatMessage>,val nextCursor:String?)
data class ReviewItem(val id:String,val agentId:String,val title:String,val summary:String,val status:String,val revision:Int,val files:List<ReviewFile>)
data class ReviewFile(val path:String,val additions:Int,val deletions:Int,val patch:String)
data class ReviewPage(val reviews:List<ReviewItem>,val nextCursor:String?)
data class AlertItem(val id:String,val severity:String,val title:String,val message:String,val timestamp:String,val agentId:String?)
data class AlertPage(val alerts:List<AlertItem>,val nextCursor:String?)
fun parseChatPage(raw:String):ChatPage {
 val o=JSONObject(raw)
 return ChatPage(o.items("messages"){ChatMessage(it.s("id"),it.s("agentId"),it.s("sender"),it.s("text"),it.s("timestamp"))},o.n("nextCursor"))
}
fun parseReviewPage(raw:String):ReviewPage {
 val o=JSONObject(raw)
 return ReviewPage(o.items("reviews"){r->ReviewItem(r.s("id"),r.s("agentId"),r.s("title"),r.s("summary"),r.s("status"),r.optInt("revision"),r.items("files"){f->ReviewFile(f.s("path"),f.optInt("additions"),f.optInt("deletions"),f.s("patch"))})},o.n("nextCursor"))
}
fun parseAlertPage(raw:String):AlertPage {
 val o=JSONObject(raw)
 return AlertPage(o.items("alerts"){AlertItem(it.s("id"),it.s("severity"),it.s("title"),it.s("message"),it.s("timestamp"),it.n("agentId"))},o.n("nextCursor"))
}
