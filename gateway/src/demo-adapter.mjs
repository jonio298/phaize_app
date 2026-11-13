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
  async control(){return {status:'forbidden'};}
 };
}
