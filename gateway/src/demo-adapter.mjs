const now=()=>new Date().toISOString();
export function createDemoAdapter(){
 let revision=1;
 const approvals=[
  {id:'ap-100',revision:1,agentId:'codex',type:'file_write',title:'Write scheduler.ts',detail:'Update task dispatcher',resource:'C:/Projects/PhAize/src/chat/scheduler.ts',scope:'single_operation',risk:'high',status:'pending',expiresAt:new Date(Date.now()+3600000).toISOString(),createdAt:now()},
  {id:'ap-101',revision:1,agentId:'claude',type:'file_delete',title:'Delete test artifacts',detail:'Remove generated fixtures',resource:'C:/Projects/PhAize/tmp/fixtures',scope:'single_operation',risk:'critical',status:'pending',expiresAt:new Date(Date.now()+3600000).toISOString(),createdAt:now()}];
 const agents=[{id:'codex',name:'Codex',status:'working',role:'Implementer',task:'Android integration',cpu:9,memoryMb:1800},{id:'claude',name:'Claude Code',status:'reviewing',role:'Reviewer',task:'Review gateway',cpu:4,memoryMb:1700}];
 const tasks=[{id:'T-01',title:'Build Android screens',agentId:'codex',status:'in_progress',progress:60},{id:'T-02',title:'Review mobile security',agentId:'claude',status:'review',progress:90}];
 const files=[{id:'f1',path:'src/chat/scheduler.ts',action:'modified',agentId:'codex',timestamp:now(),risk:'medium'},{id:'f2',path:'src/native/task_coordination.rs',action:'read',agentId:'claude',timestamp:now(),risk:'low'}];
 const timeline=[{id:'e1',revision:1,type:'task',title:'Mobile module started',detail:'Demo event; not real desktop activity',timestamp:now(),agentId:'codex'}];
 const minutes=m=>new Date(Date.now()-m*60000).toISOString();
 const conversations=[
  {id:'chat-codex',title:'Android gateway integration',agentId:'codex',messages:[
   {id:'m1',role:'user',speaker:'You',text:'Add chat viewing to the mobile app. Read only for now.',timestamp:minutes(52)},
   {id:'m2',role:'agent',speaker:'Codex',text:'On it. I will add GET /v1/chats and GET /v1/chats/:id to the gateway, then a read-only screen.',timestamp:minutes(50)},
   {id:'m3',role:'system',speaker:'PhAize',text:'Task T-01 assigned to Codex. This grants no permissions.',timestamp:minutes(49)},
   {id:'m4',role:'agent',speaker:'Codex',text:'Gateway routes are in. The response is whitelisted and bounded:\n```js\nGET /v1/chats        -> { availability, chats: [...] }\nGET /v1/chats/:id    -> { id, title, agentId, messages: [...] }\n```\nNext: the Android screen.',timestamp:minutes(31)},
   {id:'m5',role:'user',speaker:'You',text:'Nice. Keep approvals out of the chat view.',timestamp:minutes(29)},
   {id:'m6',role:'agent',speaker:'Codex',text:'Understood. Chats are read only and approvals stay on their own screen with the native re-check.',timestamp:minutes(28)}]},
  {id:'chat-claude',title:'Security review of the bridge',agentId:'claude',messages:[
   {id:'m1',role:'agent',speaker:'Claude Code',text:'Reviewing the desktop bridge contract against the gateway. Two criteria checked so far.',timestamp:minutes(75)},
   {id:'m2',role:'agent',speaker:'Claude Code',text:'Finding: the gateway must never forward a phone decision without the native desktop re-checking the revision and expiry. Confirmed in the contract.',timestamp:minutes(61)},
   {id:'m3',role:'system',speaker:'PhAize',text:'Peer approved, not verified. Native checks are still pending.',timestamp:minutes(60)}]},
  {id:'chat-room',title:'Shared room',agentId:'room',messages:[
   {id:'m1',role:'user',speaker:'You',text:'Goal: ship the phone companion preview.',timestamp:minutes(120)},
   {id:'m2',role:'system',speaker:'PhAize',text:'Task t1 assigned to Codex. Task t2 assigned to Claude Code.',timestamp:minutes(118)}]}];
 return {
  async snapshot(){return {schemaVersion:1,revision,generatedAt:now(),mode:'demo',host:{name:'PhAize Demo',online:false,monitoring:'demo',containment:'demo',cpu:0,memoryPercent:0},agents,tasks,approvals:approvals.filter(a=>a.status==='pending'),files,timeline,statistics:{eventsToday:1,blockedToday:0,completedToday:0}};},
  async decide({approvalId,decision,expectedRevision,deviceId,requestId}){
   const a=approvals.find(a=>a.id===approvalId);if(!a)return {status:'not_found'};
   if(a.status!=='pending'||a.revision!==expectedRevision)return {status:'conflict'};
   if(Date.parse(a.expiresAt)<Date.now())return {status:'expired'};
   a.status=decision==='deny'?'denied':'approved';a.revision++;revision++;
   timeline.unshift({id:'e'+revision,revision,type:'approval',title:decision==='deny'?'Denied':'Approved once',detail:a.title+' (demo only)',timestamp:now(),agentId:a.agentId,requestId,deviceId});
   return {status:'ok',result:{approvalId,decision,applied:true,revision:a.revision}};
  },
  async chats(){return conversations.map(c=>({id:c.id,title:c.title,agentId:c.agentId,preview:c.messages.at(-1).text.replace(/\s+/g,' ').slice(0,120),updatedAt:c.messages.at(-1).timestamp,messageCount:c.messages.length}));},
  async chat(id){return conversations.find(c=>c.id===id)??null;},
  async control(){return {status:'forbidden'};}
 };
}
