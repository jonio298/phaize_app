import http from 'node:http';
import {createHash,timingSafeEqual} from 'node:crypto';
const MAX_BODY=4096;
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
function validDecision(d){return exact(d,['decision','deviceId','expectedRevision','requestId'])&&['approve_once','deny'].includes(d.decision)&&ID.test(d.deviceId??'')&&ID.test(d.requestId??'')&&revision(d.expectedRevision);}
function validControl(d){return exact(d,['action','deviceId','expectedRevision','requestId'])&&['pause','resume','emergency_stop'].includes(d.action)&&ID.test(d.deviceId??'')&&ID.test(d.requestId??'')&&revision(d.expectedRevision);}
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
   const approval=url.pathname.match(/^\/v1\/approvals\/([a-zA-Z0-9_-]{1,80})\/decisions$/);
   const agent=url.pathname.match(/^\/v1\/agents\/([a-zA-Z0-9_-]{1,80})\/control$/);
   if(req.method==='POST'&&(approval||agent)){
     if(req.headers['content-type']?.split(';')[0].trim()!=='application/json')return json(res,415,{error:'json_required'});
     const body=await parseBody(req);
     if(!(approval?validDecision(body):validControl(body)))return json(res,400,{error:'invalid_payload'});
     if(agent&&!allowControl)return json(res,403,{error:'controls_disabled'});
     if(agent&&typeof adapter.control!=='function')return json(res,503,{error:'native_control_unavailable'});
     const target=(approval??agent)[1];
     const scope=approval?'approval':'agent';
     const fingerprint=createHash('sha256').update(JSON.stringify({scope,target,body})).digest('hex');
     const id=body.deviceId+':'+body.requestId;
     const prior=idempotent.get(id);
     if(prior){if(prior.fingerprint!==fingerprint)return json(res,409,{error:'idempotency_key_reused'});return json(res,prior.status,prior.payload);}
     const command={...body,...(approval?{approvalId:target}:{agentId:target})};
     // Native service must recheck authorization, revision, device, policy, scope and TTL.
     const outcome=await (approval?adapter.decide(command):adapter.control(command));
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
