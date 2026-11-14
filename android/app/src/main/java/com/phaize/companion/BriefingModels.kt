package com.phaize.companion

import org.json.JSONObject

data class ProjectBriefing(
 val generatedAt:String,val workspace:String,val since:String,val summary:String,
 val filesChanged:Int,val completed:List<String>,val blockers:List<String>,val decisions:List<String>
)
data class BuildRun(
 val id:String,val title:String,val workflow:String,val branch:String,val status:String,
 val conclusion:String,val updatedAt:String,val url:String
)
data class BuildFeed(val generatedAt:String,val repository:String,val runs:List<BuildRun>)

private fun JSONObject.text(name:String)=optString(name,"")
private fun JSONObject.strings(name:String):List<String>{
 val a=optJSONArray(name)?:return emptyList()
 return (0 until a.length()).mapNotNull { index ->
  a.optString(index).takeIf{it.isNotBlank()} ?: a.optJSONObject(index)?.optString("title")?.takeIf{it.isNotBlank()}
 }.take(30)
}
fun parseProjectBriefing(content:String):ProjectBriefing{
 val o=JSONObject(content)
 return ProjectBriefing(o.text("generatedAt"),o.text("workspace"),o.text("since"),
  o.text("summary"),o.optInt("filesChanged",0).coerceAtLeast(0),
  o.strings("completed"),o.strings("blockers"),o.strings("decisions"))
}
fun parseBuildFeed(content:String):BuildFeed{
 val o=JSONObject(content);val items=o.optJSONArray("runs")
 val runs=(0 until (items?.length()?:0)).mapNotNull{idx->
  items?.optJSONObject(idx)?.let{
   BuildRun(it.text("id"),it.text("title"),it.text("workflow"),it.text("branch"),
    it.text("status"),it.text("conclusion"),it.text("updatedAt"),it.text("url"))
  }
 }.take(50)
 return BuildFeed(o.text("generatedAt"),o.text("repository"),runs)
}
