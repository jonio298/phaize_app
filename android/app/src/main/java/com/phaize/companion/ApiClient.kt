package com.phaize.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import java.util.UUID

class ApiClient {
 var lanFingerprint:String?=null
 private suspend fun call(base:String,token:String,path:String,method:String="GET",payload:JSONObject?=null):String=withContext(Dispatchers.IO){
  val u=URL(base.trimEnd('/')+path)
  require(u.protocol=="https"&&u.userInfo==null&&u.host.isNotBlank()){ "Trusted HTTPS gateway required" }
  val conn=u.openConnection() as HttpsURLConnection
  lanFingerprint?.let{PinnedLanTls.apply(conn,u.host,it)}
  try {
   conn.connectTimeout=8000;conn.readTimeout=12000;conn.requestMethod=method;conn.instanceFollowRedirects=false
   conn.setRequestProperty("Authorization","Bearer $token")
   conn.setRequestProperty("Accept","application/json")
   if(payload!=null){conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json");conn.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}}
   val status=conn.responseCode
   val response=(if(status in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()} ?: ""
   if(status !in 200..299)throw IllegalStateException("HTTP $status: "+response.take(160))
   response
  }finally{conn.disconnect()}
 }
 suspend fun enroll(invite:LanInvitation):String {
  val body=JSONObject().put("secret",invite.secret).put("name","Android companion")
  val previous=lanFingerprint
  return try {
   lanFingerprint=invite.fingerprint
   val result=JSONObject(call(invite.base,"","/v1/pair","POST",body))
   val token=result.getString("token")
   require(token.matches(Regex("[0-9a-f]{64}")) && result.optString("scope")=="read_only"){"Invalid desktop pairing response"}
   token
  }catch(error:Exception){lanFingerprint=previous;throw error}
 }
 suspend fun snapshot(base:String,token:String)=parseSnapshot(call(base,token,"/v1/snapshot"))
 /** Read-only conversation views. The id is encoded, and the gateway rejects anything that is not a plain id. */
 suspend fun chats(base:String,token:String)=parseChatList(call(base,token,"/v1/chats"))
 suspend fun chat(base:String,token:String,id:String)=parseChatMessages(call(base,token,"/v1/chats/"+java.net.URLEncoder.encode(id,"UTF-8")))
 /** The changelog and the handoffs between agents. Read only. */
 suspend fun docs(base:String,token:String)=parseDocs(call(base,token,"/v1/docs"))
 suspend fun briefing(base:String,token:String):ProjectBriefing=parseProjectBriefing(call(base,token,"/v1/briefing"))
 suspend fun builds(base:String,token:String):BuildFeed=parseBuildFeed(call(base,token,"/v1/builds"))
 suspend fun decide(base:String,token:String,a:Approval,approve:Boolean,deviceId:String):String {
  val p=JSONObject().put("decision",if(approve)"approve_once" else "deny").put("deviceId",deviceId).put("expectedRevision",a.revision).put("requestId",UUID.randomUUID().toString())
  return call(base,token,"/v1/approvals/${a.id}/decisions","POST",p)
 }
 suspend fun createTask(base:String,token:String,title:String,description:String,agentId:String?,priority:String,revision:Int,deviceId:String):String {
  require(title.trim().length in 3..160 && description.length<=4000 && priority in listOf("low","normal","high"))
  val p=JSONObject().put("title",title.trim()).put("description",description).put("agentId",agentId).put("priority",priority)
   .put("deviceId",deviceId).put("expectedRevision",revision).put("requestId",UUID.randomUUID().toString())
  return call(base,token,"/v1/tasks","POST",p)
 }
 suspend fun browse(base:String,token:String,parentId:String?=null):BrowserPage {
  val path="/v1/files/browse"+(if(parentId==null) "" else "?parent="+java.net.URLEncoder.encode(parentId,"UTF-8"))
  return parseBrowserPage(call(base,token,path))
 }
 suspend fun errors(base:String,token:String,after:String?=null):ErrorPage {
  val path="/v1/errors"+(if(after==null) "" else "?after="+java.net.URLEncoder.encode(after,"UTF-8"))
  return parseErrorPage(call(base,token,path))
 }
 /** Their write-capable agent chat route; the desktop does not serve it and the app does not call it. */
 suspend fun agentChat(base:String,token:String,agentId:String):ChatPage =
  parseChatPage(call(base,token,"/v1/agents/"+agentId+"/chat"))
 suspend fun reviews(base:String,token:String):ReviewPage =
  parseReviewPage(call(base,token,"/v1/reviews"))
 suspend fun alerts(base:String,token:String):AlertPage =
  parseAlertPage(call(base,token,"/v1/alerts"))
 suspend fun sendChat(base:String,token:String,agentId:String,message:String,revision:Int,deviceId:String):String {
  require(message.isNotBlank()&&message.length<=4000)
  val p=JSONObject().put("message",message.trim()).put("deviceId",deviceId).put("expectedRevision",revision).put("requestId",UUID.randomUUID().toString())
  return call(base,token,"/v1/agents/"+agentId+"/chat","POST",p)
 }
 suspend fun createGoal(base:String,token:String,goal:String,instructions:String,priority:String,revision:Int,deviceId:String):String {
  require(goal.trim().length in 3..240&&instructions.length<=4000&&priority in listOf("low","normal","high"))
  val p=JSONObject().put("goal",goal.trim()).put("instructions",instructions).put("priority",priority)
   .put("deviceId",deviceId).put("expectedRevision",revision).put("requestId",UUID.randomUUID().toString())
  return call(base,token,"/v1/goals","POST",p)
 }
 suspend fun reviewDecision(base:String,token:String,reviewId:String,decision:String,revision:Int,deviceId:String):String {
  require(decision in listOf("approve_review","request_changes"))
  val p=JSONObject().put("decision",decision).put("deviceId",deviceId).put("expectedRevision",revision).put("requestId",UUID.randomUUID().toString())
  return call(base,token,"/v1/reviews/"+reviewId+"/decision","POST",p)
 }
 suspend fun operations(base:String,token:String):OpsPage=parseOpsPage(call(base,token,"/v1/ops"))
 suspend fun opsAction(base:String,token:String,action:String,params:JSONObject,revision:Int,deviceId:String):String {
  require(action in listOf("lockdown","limits","terminal_preset","checkpoint_restore","capture"))
  val p=JSONObject().put("action",action).put("deviceId",deviceId)
   .put("expectedRevision",revision).put("requestId",UUID.randomUUID().toString())
  val keys=params.keys()
  while(keys.hasNext()){val key=keys.next();require(key !in listOf("action","deviceId","expectedRevision","requestId"));p.put(key,params.get(key))}
  return call(base,token,"/v1/ops/action","POST",p)
 }
 suspend fun permissions(base:String,token:String,a:Agent,read:String,write:String,delete:String,revision:Int,deviceId:String):String {
  require(read in listOf("allow","ask","deny") && write in listOf("ask","deny") && delete in listOf("ask","deny"))
  val p=JSONObject().put("read",read).put("write",write).put("delete",delete).put("deviceId",deviceId).put("expectedRevision",revision).put("requestId",UUID.randomUUID().toString())
  return call(base,token,"/v1/agents/${a.id}/permissions","POST",p)
 }
 suspend fun control(base:String,token:String,a:Agent,action:String,revision:Int,deviceId:String):String {
  require(action in listOf("start","pause","resume","emergency_stop"))
  val p=JSONObject().put("action",action).put("deviceId",deviceId).put("expectedRevision",revision).put("requestId",UUID.randomUUID().toString())
  return call(base,token,"/v1/agents/${a.id}/control","POST",p)
 }
}
