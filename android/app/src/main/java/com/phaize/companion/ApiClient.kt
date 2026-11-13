package com.phaize.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ApiClient {
 private suspend fun call(base:String,token:String,path:String,method:String="GET",payload:JSONObject?=null):String=withContext(Dispatchers.IO){
  val u=URL(base.trimEnd('/')+path)
  require(u.protocol=="https"&&u.userInfo==null&&u.host.isNotBlank()){ "Trusted HTTPS gateway required" }
  val conn=u.openConnection() as HttpURLConnection
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
 suspend fun snapshot(base:String,token:String)=parseSnapshot(call(base,token,"/v1/snapshot"))
 suspend fun decide(base:String,token:String,a:Approval,approve:Boolean,deviceId:String):String {
  val p=JSONObject().put("decision",if(approve)"approve_once" else "deny").put("deviceId",deviceId).put("expectedRevision",a.revision).put("requestId",UUID.randomUUID().toString())
  return call(base,token,"/v1/approvals/${a.id}/decisions","POST",p)
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
