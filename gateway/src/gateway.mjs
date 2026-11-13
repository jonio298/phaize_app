import http from 'node:http';
import {createHash,timingSafeEqual} from 'node:crypto';
const MAX_BODY=8192;
const ID=/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$/;
const json=(res,status,obj)=>{res.writeHead(status,{'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff','referrer-policy':'no-referrer','content-security-policy':"default-src 'none'"});res.end(JSON.stringify(obj));};
const validToken=(authorization,secret)=>{
 if(typeof authorization!=='string'||!authorization.startsWith('Bearer ')||!secret)return false;
 const x=createHash('sha256').update(authorization.slice(7)).digest();
 const y=createHash('sha256').update(secret).digest();
 return timingSafeEqual(x,y);
};
async function parseBody(req){
 let size=0;const parts=[];
 for await(const chunk of req){size+=chunk.length;if(size>MAX_BODY)throw Object.assign(Error('body too large'),{status:413});parts.push(chunk);}
 let body;try{body=JSON.parse(Buffer.concat(parts).toString('utf8'));}catch{throw Object.assign(Error('invalid json'),{status:400});}
 if(!body||typeof body!=='object'||Array.isArray(body))throw Object.assign(Error('object required'),{status:400});
 return body;
}
const exact=(x,fields)=>Object.keys(x).sort().join('|')===fields.sort().join('|');
const revision=n=>Number.isSafeInteger(n)&&n>=0;
const OPAQUE=/^[a-zA-Z0-9_-]{1,128}$/;
function validTask(d){return exact(d,['title','description','agentId','priority','deviceId','expectedRevision','requestId'])&&typeof d.title==='string'&&d.title.trim().length>=3&&d.title.length<=160&&typeof d.description==='string'&&d.description.length<=4000&&(d.agentId===null||(typeof d.agentId==='string'&&ID.test(d.agentId)))&&['low','normal','high'].includes(d.priority)&&ID.test(d.deviceId??'')&&ID.test(d.requestId??'')&&revision(d.expectedRevision);}

const REMOTE_ACTIONS=['lockdown','run_command','restore_checkpoint','set_limits','capture_preview','refresh_git'];
function validRemote(d){
 if(!d||!REMOTE_ACTIONS.includes(d.action)||!ID.test(d.deviceId??'')||!ID.test(d.requestId??'')||!revision(d.expectedRevision))return false;
 const base=['action','deviceId','expectedRevision','requestId'];
 switch(d.action){
  case 'lockdown': return exact(d,[...base,'reason'])&&typeof d.reason==='string'&&d.reason.trim().length>=8&&d.reason.length<=240;
  case 'run_command': return exact(d,[...base,'commandId'])&&ID.test(d.commandId??'');
  case 'restore_checkpoint': return exact(d,[...base,'checkpointId'])&&ID.test(d.checkpointId??'');
  case 'set_limits': return exact(d,[...base,'agentId','cpuPercent','memoryMb','networkMode'])&&ID.test(d.agentId??'')&&Number.isSafeInteger(d.cpuPercent)&&d.cpuPercent>=5&&d.cpuPercent<=100&&Number.isSafeInteger(d.memoryMb)&&d.memoryMb>=256&&d.memoryMb<=32768&&['off','restricted','standard'].includes(d.networkMode);
  case 'capture_preview': return exact(d,base);
  case 'refresh_git': return exact(d,base);
  default: return false;
 }
}
function validChat(d){return exact(d,['message','deviceId','expectedRevision','requestId'])&&typeof d.message==='string'&&d.message.trim().length>0&&d.message.length<=4000&&ID.test(d.deviceId??'')&&ID.test(d.requestId??'')&&revision(d.expectedRevision);}
function validGoal(d){return exact(d,['goal','instructions','priority','deviceId','expectedRevision','requestId'])&&typeof d.goal==='string'&&d.goal.trim().length>=3&&d.goal.length<=240&&typeof d.instructions==='string'&&d.instructions.length<=4000&&['low','normal','high'].includes(d.priority)&&ID.test(d.deviceId??'')&&ID.test(d.requestId??'')&&revision(d.expectedRevision);}
function validReview(d){return exact(d,['decision','deviceId','expectedRevision','requestId'])&&['request_changes','approve_review'].includes(d.decision)&&ID.test(d.deviceId??'')&&ID.test(d.requestId??'')&&revision(d.expectedRevision);}
function validDecision(d){return exact(d,['decision','deviceId','expectedRevision','requestId'])&&['approve_once','deny'].includes(d.decision)&&ID.test(d.deviceId??'')&&ID.test(d.requestId??'')&&revision(d.expectedRevision);}
function validControl(d){return exact(d,['action','deviceId','expectedRevision','requestId'])&&['start','pause','resume','emergency_stop'].includes(d.action)&&ID.test(d.deviceId??'')&&ID.test(d.requestId??'')&&revision(d.expectedRevision);}
function validPolicy(d){return exact(d,['deviceId','expectedRevision','requestId','read','write','delete'])&&ID.test(d.deviceId??'')&&ID.test(d.requestId??'')&&revision(d.expectedRevision)&&['allow','ask','deny'].includes(d.read)&&['ask','deny'].includes(d.write)&&['ask','deny'].includes(d.delete);}
function resultStatus(outcome){return {ok:200,not_found:404,expired:410,conflict:409,forbidden:403,unavailable:503}[outcome?.status]??503;}
export function createGateway({adapter,token,rateLimit=60,allowControl=false}){
 if(!adapter||typeof adapter.snapshot!=='function'||typeof adapter.decide!=='function')throw Error('desktop adapter required');
 if(typeof token!=='string'||token.length<32)throw Error('gateway bearer must be at least 32 characters');
 const attempts=new Map(),idempotent=new Map();
 async function handler(req,res){
  try{
   const url=new URL(req.url,'http://localhost');
   if(req.method==='GET'&&url.pathname==='/health')return json(res,200,{ok:true,service:'phaize-mobile-gateway'});
   if(!validToken(req.headers.authorization,token))return json(res,401,{error:'unauthorized'});
   const now=Date.now(),key='authenticated',state=attempts.get(key)??{started:now,count:0};
   if(now-state.started>=60000){state.started=now;state.count=0;}state.count++;attempts.set(key,state);
   if(state.count>rateLimit)return json(res,429,{error:'rate_limited'});
   if(req.method==='GET'&&url.pathname==='/v1/snapshot')return json(res,200,await adapter.snapshot());
   if(req.method==='GET'&&url.pathname==='/v1/events'){
     const from=Number(url.searchParams.get('after')??0);
     if(!revision(from))return json(res,400,{error:'invalid_cursor'});
     const s=await adapter.snapshot();
     return json(res,200,{revision:s.revision,events:(s.timeline??[]).filter(e=>Number.isInteger(e.revision)&&e.revision>from).slice(-100),resync:from>s.revision});
   }
   if(req.method==='GET'&&url.pathname==='/v1/files/browse'){
     if(typeof adapter.browse!=='function')return json(res,503,{error:'native_browser_unavailable'});
     const keys=[...url.searchParams.keys()];
     if(keys.some(k=>k!=='parent')||keys.length>1)return json(res,400,{error:'invalid_query'});
     const parent=url.searchParams.get('parent');
     if(parent!==null&&!OPAQUE.test(parent))return json(res,400,{error:'invalid_parent'});
     const outcome=await adapter.browse({parentId:parent});
     if(outcome?.status&&outcome.status!=='ok')return json(res,resultStatus(outcome),{error:outcome.status});
     return json(res,200,{parentId:outcome?.parentId??null,entries:Array.isArray(outcome?.entries)?outcome.entries.slice(0,200):[],truncated:Boolean(outcome?.truncated)});
   }
   if(req.method==='GET'&&url.pathname==='/v1/errors'){
     if(typeof adapter.errors!=='function')return json(res,503,{error:'native_logs_unavailable'});
     const keys=[...url.searchParams.keys()];
     if(keys.some(k=>k!=='after')||keys.length>1)return json(res,400,{error:'invalid_query'});
     const after=url.searchParams.get('after');
     if(after!==null&&!OPAQUE.test(after))return json(res,400,{error:'invalid_cursor'});
     const outcome=await adapter.errors({after});
     if(outcome?.status&&outcome.status!=='ok')return json(res,resultStatus(outcome),{error:outcome.status});
     return json(res,200,{entries:Array.isArray(outcome?.entries)?outcome.entries.slice(0,200):[],nextCursor:outcome?.nextCursor??null});
   }
   if(req.method==='GET'&&url.pathname==='/v1/remote/console'){
     if(url.search!=='')return json(res,400,{error:'invalid_query'});
     if(typeof adapter.remoteStatus!=='function')return json(res,503,{error:'native_remote_unavailable'});
     const outcome=await adapter.remoteStatus();
     if(outcome?.status&&outcome.status!=='ok')return json(res,resultStatus(outcome),{error:outcome.status});
     return json(res,200,{revision:outcome?.revision??0,locked:Boolean(outcome?.locked),
       resources:Array.isArray(outcome?.resources)?outcome.resources.slice(0,40):[],
       commands:Array.isArray(outcome?.commands)?outcome.commands.slice(0,30):[],
       checkpoints:Array.isArray(outcome?.checkpoints)?outcome.checkpoints.slice(0,40):[],
       git:outcome?.git??null,preview:outcome?.preview??null});
   }
   const readRoute=(url.pathname.match(/^\/v1\/agents\/([a-zA-Z0-9_-]{1,80})\/chat$/)&&'chat')||(url.pathname==='/v1/reviews'&&'reviews')||(url.pathname==='/v1/alerts'&&'alerts');
   if(req.method==='GET'&&readRoute){
     const keys=[...url.searchParams.keys()];
     if(keys.some(k=>k!=='after')||keys.length>1)return json(res,400,{error:'invalid_query'});
     const after=url.searchParams.get('after');
     if(after!==null&&!OPAQUE.test(after))return json(res,400,{error:'invalid_cursor'});
     const agentId=readRoute==='chat'?url.pathname.split('/')[3]:null;
     const method=readRoute==='chat'?'chat':readRoute;
     if(typeof adapter[method]!=='function')return json(res,503,{error:'native_'+method+'_unavailable'});
     const outcome=await adapter[method]({agentId,after});
     if(outcome?.status&&outcome.status!=='ok')return json(res,resultStatus(outcome),{error:outcome.status});
     const list=readRoute==='chat'?'messages':readRoute==='reviews'?'reviews':'alerts';
     return json(res,200,{[list]:Array.isArray(outcome?.[list])?outcome[list].slice(0,100):[],nextCursor:outcome?.nextCursor??null});
   }
   const approval=url.pathname.match(/^\/v1\/approvals\/([a-zA-Z0-9_-]{1,80})\/decisions$/);
   const agent=url.pathname.match(/^\/v1\/agents\/([a-zA-Z0-9_-]{1,80})\/control$/);
   const policy=url.pathname.match(/^\/v1\/agents\/([a-zA-Z0-9_-]{1,80})\/permissions$/);
   const task=url.pathname==='/v1/tasks'&&req.method==='POST';
   const remote=url.pathname==='/v1/remote/actions'&&req.method==='POST';
   const goal=url.pathname==='/v1/goals'&&req.method==='POST';
   const chat=url.pathname.match(/^\/v1\/agents\/([a-zA-Z0-9_-]{1,80})\/chat$/);
   const review=url.pathname.match(/^\/v1\/reviews\/([a-zA-Z0-9_-]{1,80})\/decision$/);
   if(req.method==='POST'&&(approval||agent||policy||task||goal||chat||review||remote)){
     if(req.headers['content-type']?.split(';')[0].trim()!=='application/json')return json(res,415,{error:'json_required'});
     const body=await parseBody(req);
     if(!(approval?validDecision(body):policy?validPolicy(body):task?validTask(body):goal?validGoal(body):chat?validChat(body):review?validReview(body):remote?validRemote(body):validControl(body)))return json(res,400,{error:'invalid_payload'});
     if((agent||policy||task||goal||chat||review||remote)&&!allowControl)return json(res,403,{error:'controls_disabled'});
     if(remote&&typeof adapter.remoteAction!=='function')return json(res,503,{error:'native_remote_unavailable'});
     if(goal&&typeof adapter.createGoal!=='function')return json(res,503,{error:'native_goals_unavailable'});
     if(chat&&typeof adapter.sendChat!=='function')return json(res,503,{error:'native_chat_unavailable'});
     if(review&&typeof adapter.reviewDecision!=='function')return json(res,503,{error:'native_review_unavailable'});
     if(task&&typeof adapter.createTask!=='function')return json(res,503,{error:'native_tasks_unavailable'});
     if(policy&&typeof adapter.permissions!=='function')return json(res,503,{error:'native_permissions_unavailable'});
     if(agent&&typeof adapter.control!=='function')return json(res,503,{error:'native_control_unavailable'});
     const target=(task||goal||remote)?'new':(approval??agent??policy??chat??review)[1];
     const scope=approval?'approval':policy?'policy':task?'task':goal?'goal':chat?'chat':review?'review':remote?'remote':'agent';
     const fingerprint=createHash('sha256').update(JSON.stringify({scope,target,body})).digest('hex');
     const id=body.deviceId+':'+body.requestId;
     const prior=idempotent.get(id);
     if(prior){if(prior.fingerprint!==fingerprint)return json(res,409,{error:'idempotency_key_reused'});return json(res,prior.status,prior.payload);}
     const command={...body,...(approval?{approvalId:target}:review?{reviewId:target}:(task||goal||remote)?{}:{agentId:target})};
     // Native service must recheck authorization, revision, device, policy, scope and TTL.
     const outcome=await (approval?adapter.decide(command):policy?adapter.permissions(command):task?adapter.createTask(command):goal?adapter.createGoal(command):chat?adapter.sendChat(command):review?adapter.reviewDecision(command):remote?adapter.remoteAction(command):adapter.control(command));
     const status=resultStatus(outcome);
     const payload=status===200?(outcome.result??{ok:true}):{error:outcome?.status??'unavailable'};
     if(status!==503){idempotent.set(id,{fingerprint,status,payload});if(idempotent.size>5000)idempotent.delete(idempotent.keys().next().value);}
     return json(res,status,payload);
   }
   return json(res,404,{error:'not_found'});
  }catch(e){return json(res,e.status??503,{error:e.status?e.message:'unavailable'});}
 }
 return http.createServer(handler);
}
