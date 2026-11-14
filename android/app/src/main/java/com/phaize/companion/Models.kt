package com.phaize.companion

import org.json.JSONArray
import org.json.JSONObject

data class Agent(val id:String,val name:String,val status:String,val role:String,val task:String?,val cpu:Int,val memoryMb:Int,val metricsAvailable:Boolean=false,val readPermission:String="allow",val writePermission:String="ask",val deletePermission:String="ask")
data class WorkTask(val id:String,val title:String,val agentId:String?,val status:String,val progress:Int,val detail:String?=null)
/** Who is working right now and on which step, as the desktop reports it. */
data class LiveAgent(val id:String,val name:String,val status:String,val working:Boolean,val step:String?)
/** One request this phone made to the desktop and how it turned out. */
data class PhoneRequest(val id:String,val kind:String,val status:String,val message:String)
data class Approval(val id:String,val revision:Int,val agentId:String,val title:String,val detail:String,val resource:String,val type:String,val risk:String,val expiresAt:String)
data class FileEvent(val id:String,val path:String,val action:String,val agentId:String,val timestamp:String,val risk:String)
data class BrowserEntry(val id:String,val name:String,val kind:String,val sizeBytes:Long,val modifiedAt:String,val risk:String)
data class BrowserPage(val parentId:String?,val entries:List<BrowserEntry>,val truncated:Boolean)
data class ErrorEntry(val id:String,val severity:String,val source:String,val message:String,val timestamp:String,val agentId:String?)
data class ErrorPage(val entries:List<ErrorEntry>,val nextCursor:String?)
data class Activity(val id:String,val type:String,val title:String,val detail:String,val timestamp:String,val agentId:String?)
data class Host(val name:String,val online:Boolean,val monitoring:String,val containment:String,val cpu:Int,val memoryPercent:Int)
data class Snapshot(val revision:Int,val mode:String,val host:Host,val agents:List<Agent>,val tasks:List<WorkTask>,val approvals:List<Approval>,val files:List<FileEvent>,val timeline:List<Activity>,val eventsToday:Int,val blockedToday:Int,val completedToday:Int,val approvalsAvailable:Boolean,val controlsAvailable:Boolean,val tasksAvailable:Boolean,val sampledEvents:Int,val liveAgents:List<LiveAgent> = emptyList())
private fun <T> JSONObject.items(name:String,make:(JSONObject)->T):List<T>{val a=optJSONArray(name)?:JSONArray();return (0 until a.length()).mapNotNull{a.optJSONObject(it)?.let(make)}}
private fun JSONObject.s(k:String)=optString(k,"")
private fun JSONObject.n(k:String)=if(isNull(k))null else optString(k).takeIf{it.isNotEmpty()}
fun parseSnapshot(text:String):Snapshot {
 val o=JSONObject(text);val h=o.getJSONObject("host");val stats=o.optJSONObject("statistics")?:JSONObject()
 return Snapshot(o.optInt("revision"),o.s("mode"),Host(h.s("name"),h.optBoolean("online"),h.s("monitoring"),h.s("containment"),h.optInt("cpu"),h.optInt("memoryPercent")),
 o.items("agents"){Agent(it.s("id"),it.s("name"),it.s("status"),it.s("role"),it.n("task"),it.optInt("cpu"),it.optInt("memoryMb"),it.optBoolean("metricsAvailable",false),it.optJSONObject("permissions")?.optString("read","allow")?:"allow",it.optJSONObject("permissions")?.optString("write","ask")?:"ask",it.optJSONObject("permissions")?.optString("delete","ask")?:"ask")},
 o.items("tasks"){WorkTask(it.s("id"),it.s("title"),it.n("agentId"),it.s("status"),it.optInt("progress"),it.n("detail"))},
 o.items("approvals"){Approval(it.s("id"),it.optInt("revision"),it.s("agentId"),it.s("title"),it.s("detail"),it.s("resource"),it.s("type"),it.s("risk"),it.s("expiresAt"))},
 o.items("files"){FileEvent(it.s("id"),it.s("path"),it.s("action"),it.s("agentId"),it.s("timestamp"),it.s("risk"))},
 o.items("timeline"){Activity(it.s("id"),it.s("type"),it.s("title"),it.s("detail"),it.s("timestamp"),it.n("agentId"))},
 stats.optInt("eventsToday"),stats.optInt("blockedToday"),stats.optInt("completedToday"),o.optString("approvalsAvailability","not_connected")=="connected",o.optString("controlsAvailability","not_connected")=="connected",o.optString("tasksAvailability","not_connected")=="connected",stats.optInt("sampledEvents"),o.items("liveAgents"){LiveAgent(it.s("id"),it.s("name"),it.s("status"),it.optBoolean("working"),it.n("step"))})
}

data class ChatSummary(val id:String,val title:String,val agentId:String,val preview:String,val updatedAt:String,val messageCount:Int,val working:Boolean=false)
data class ChatMessage(val id:String,val role:String,val speaker:String,val text:String,val timestamp:String)
data class ChatList(val available:Boolean,val chats:List<ChatSummary>)
fun parseChatList(text:String):ChatList {
 val o=JSONObject(text)
 return ChatList(o.optString("availability","not_connected")=="connected",
  o.items("chats"){ChatSummary(it.s("id"),it.s("title"),it.s("agentId"),it.s("preview"),it.s("updatedAt"),it.optInt("messageCount"),it.optBoolean("working",false))})
}
fun parseChatMessages(text:String):List<ChatMessage> {
 val o=JSONObject(text)
 return o.items("messages"){ChatMessage(it.s("id"),it.s("role"),it.s("speaker"),it.s("text"),it.s("timestamp"))}
}

/** A changelog or handoff line written by the desktop. */
data class DocEntry(val id:String,val at:String,val who:String,val text:String)
data class DocsView(val available:Boolean,val changelog:List<DocEntry>,val handoffs:List<DocEntry>)
fun parseDocs(text:String):DocsView {
 val o=JSONObject(text)
 val make={e:JSONObject->DocEntry(e.s("id"),e.s("at"),e.s("who"),e.s("text"))}
 return DocsView(o.optString("availability","not_connected")=="connected",o.items("changelog",make),o.items("handoffs",make))
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

data class AgentChatMessage(val id:String,val agentId:String,val sender:String,val text:String,val timestamp:String)
data class ChatPage(val messages:List<AgentChatMessage>,val nextCursor:String?)
data class ReviewItem(val id:String,val agentId:String,val title:String,val summary:String,val status:String,val revision:Int,val files:List<ReviewFile>)
data class ReviewFile(val path:String,val additions:Int,val deletions:Int,val patch:String)
data class ReviewPage(val reviews:List<ReviewItem>,val nextCursor:String?)
data class AlertItem(val id:String,val severity:String,val title:String,val message:String,val timestamp:String,val agentId:String?)
data class AlertPage(val alerts:List<AlertItem>,val nextCursor:String?)
fun parseChatPage(raw:String):ChatPage {
 val o=JSONObject(raw)
 return ChatPage(o.items("messages"){AgentChatMessage(it.s("id"),it.s("agentId"),it.s("sender"),it.s("text"),it.s("timestamp"))},o.n("nextCursor"))
}
fun parseReviewPage(raw:String):ReviewPage {
 val o=JSONObject(raw)
 return ReviewPage(o.items("reviews"){r->ReviewItem(r.s("id"),r.s("agentId"),r.s("title"),r.s("summary"),r.s("status"),r.optInt("revision"),r.items("files"){f->ReviewFile(f.s("path"),f.optInt("additions"),f.optInt("deletions"),f.s("patch"))})},o.n("nextCursor"))
}
fun parseAlertPage(raw:String):AlertPage {
 val o=JSONObject(raw)
 return AlertPage(o.items("alerts"){AlertItem(it.s("id"),it.s("severity"),it.s("title"),it.s("message"),it.s("timestamp"),it.n("agentId"))},o.n("nextCursor"))
}

data class OpsHealth(val cpuPercent:Int,val memoryPercent:Int,val diskPercent:Int,val temperatureC:Int?,val networkState:String,val coverage:String)
data class OpsGit(val branch:String,val dirtyFiles:Int,val ahead:Int,val behind:Int,val lastCommit:String)
data class OpsCheckpoint(val id:String,val title:String,val createdAt:String,val description:String)
data class OpsCapture(val id:String,val timestamp:String,val description:String,val status:String)
data class OpsCommand(val id:String,val agentId:String,val label:String,val status:String,val output:String,val timestamp:String)
data class OpsPage(val health:OpsHealth?,val git:OpsGit?,val checkpoints:List<OpsCheckpoint>,val captures:List<OpsCapture>,val terminalHistory:List<OpsCommand>)
fun parseOpsPage(raw:String):OpsPage{
 val o=JSONObject(raw)
 val h=o.optJSONObject("health")?.let{OpsHealth(it.optInt("cpuPercent"),it.optInt("memoryPercent"),it.optInt("diskPercent"),if(it.has("temperatureC")&&!it.isNull("temperatureC"))it.optInt("temperatureC") else null,it.s("networkState"),it.s("coverage"))}
 val g=o.optJSONObject("git")?.let{OpsGit(it.s("branch"),it.optInt("dirtyFiles"),it.optInt("ahead"),it.optInt("behind"),it.s("lastCommit"))}
 return OpsPage(h,g,
  o.items("checkpoints"){OpsCheckpoint(it.s("id"),it.s("title"),it.s("createdAt"),it.s("description"))},
  o.items("captures"){OpsCapture(it.s("id"),it.s("timestamp"),it.s("description"),it.s("status"))},
  o.items("terminalHistory"){OpsCommand(it.s("id"),it.s("agentId"),it.s("label"),it.s("status"),it.s("output"),it.s("timestamp"))}
 )
}

fun parseRequests(text:String):List<PhoneRequest> {
 val o=JSONObject(text)
 return o.items("requests"){PhoneRequest(it.s("id"),it.s("kind"),it.s("status"),it.s("message"))}
}
