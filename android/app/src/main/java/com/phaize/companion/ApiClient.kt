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
 suspend fun decide(base:String,token:String,a:Approval,approve:Boolean,deviceId:String):String {
  val p=JSONObject().put("decision",if(approve)"approve_once" else "deny").put("deviceId",deviceId).put("expectedRevision",a.revision).put("requestId",UUID.randomUUID().toString())
  return call(base,token,"/v1/approvals/${a.id}/decisions","POST",p)
 }
 suspend fun control(base:String,token:String,a:Agent,action:String,revision:Int,deviceId:String):String {
  require(action in listOf("pause","resume","emergency_stop"))
  val p=JSONObject().put("action",action).put("deviceId",deviceId).put("expectedRevision",revision).put("requestId",UUID.randomUUID().toString())
  return call(base,token,"/v1/agents/${a.id}/control","POST",p)
 }
}
