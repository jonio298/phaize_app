package com.phaize.companion

import org.json.JSONArray
import org.json.JSONObject

data class Agent(val id:String,val name:String,val status:String,val role:String,val task:String?,val cpu:Int,val memoryMb:Int,val metricsAvailable:Boolean)
data class WorkTask(val id:String,val title:String,val agentId:String?,val status:String,val progress:Int)
data class Approval(val id:String,val revision:Int,val agentId:String,val title:String,val detail:String,val resource:String,val type:String,val risk:String,val expiresAt:String)
data class FileEvent(val id:String,val path:String,val action:String,val agentId:String,val timestamp:String,val risk:String)
data class Activity(val id:String,val type:String,val title:String,val detail:String,val timestamp:String,val agentId:String?)
data class Host(val name:String,val online:Boolean,val monitoring:String,val containment:String,val cpu:Int,val memoryPercent:Int)
data class Snapshot(val revision:Int,val mode:String,val host:Host,val agents:List<Agent>,val tasks:List<WorkTask>,val approvals:List<Approval>,val files:List<FileEvent>,val timeline:List<Activity>,val eventsToday:Int,val blockedToday:Int,val completedToday:Int,val approvalsAvailable:Boolean,val controlsAvailable:Boolean,val tasksAvailable:Boolean,val sampledEvents:Int)
private fun <T> JSONObject.items(name:String,make:(JSONObject)->T):List<T>{val a=optJSONArray(name)?:JSONArray();return (0 until a.length()).mapNotNull{a.optJSONObject(it)?.let(make)}}
private fun JSONObject.s(k:String)=optString(k,"")
private fun JSONObject.n(k:String)=if(isNull(k))null else optString(k).takeIf{it.isNotEmpty()}
fun parseSnapshot(text:String):Snapshot {
 val o=JSONObject(text);val h=o.getJSONObject("host");val stats=o.optJSONObject("statistics")?:JSONObject()
 return Snapshot(o.optInt("revision"),o.s("mode"),Host(h.s("name"),h.optBoolean("online"),h.s("monitoring"),h.s("containment"),h.optInt("cpu"),h.optInt("memoryPercent")),
 o.items("agents"){Agent(it.s("id"),it.s("name"),it.s("status"),it.s("role"),it.n("task"),it.optInt("cpu"),it.optInt("memoryMb"),it.optBoolean("metricsAvailable",false))},
 o.items("tasks"){WorkTask(it.s("id"),it.s("title"),it.n("agentId"),it.s("status"),it.optInt("progress"))},
 o.items("approvals"){Approval(it.s("id"),it.optInt("revision"),it.s("agentId"),it.s("title"),it.s("detail"),it.s("resource"),it.s("type"),it.s("risk"),it.s("expiresAt"))},
 o.items("files"){FileEvent(it.s("id"),it.s("path"),it.s("action"),it.s("agentId"),it.s("timestamp"),it.s("risk"))},
 o.items("timeline"){Activity(it.s("id"),it.s("type"),it.s("title"),it.s("detail"),it.s("timestamp"),it.n("agentId"))},
 stats.optInt("eventsToday"),stats.optInt("blockedToday"),stats.optInt("completedToday"),o.optString("approvalsAvailability","not_connected")=="connected",o.optString("controlsAvailability","not_connected")=="connected",o.optString("tasksAvailability","not_connected")=="connected",stats.optInt("sampledEvents"))
}
