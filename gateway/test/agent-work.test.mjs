import test from 'node:test';
import assert from 'node:assert/strict';
import {createGateway} from '../src/gateway.mjs';
const token='long-development-token-test-at-least-32';
const baseFields={deviceId:'phone_1',expectedRevision:7,requestId:'req_1'};
async function withServer(adapter,run,allowControl=true){
 const server=createGateway({adapter:{snapshot:async()=>({revision:7,timeline:[]}),decide:async()=>({status:'forbidden'}),...adapter},token,allowControl,rateLimit:200});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const base='http://127.0.0.1:'+server.address().port;
 const call=async(path,method='GET',data)=>{const r=await fetch(base+path,{method,headers:{authorization:'Bearer '+token,...(data?{'content-type':'application/json'}:{})},body:data?JSON.stringify(data):undefined});return {status:r.status,payload:await r.json()};};
 try{await run(call);}finally{await new Promise(resolve=>server.close(resolve));}
}
test('chat is delivered only through native bridge and rejects unsafe extra fields',()=>withServer({sendChat:async x=>({status:'ok',result:{accepted:true,agentId:x.agentId}}),agentChat:async()=>({messages:[{id:'m1',text:'hi'}]})},async call=>{
 assert.equal((await call('/v1/agents/codex/chat')).payload.messages[0].id,'m1');
 assert.equal((await call('/v1/agents/codex/chat','POST',{...baseFields,message:'Please review'})).status,200);
 assert.equal((await call('/v1/agents/codex/chat','POST',{...baseFields,requestId:'req_2',message:'x',command:'del'})).status,400);
 assert.equal((await call('/v1/agents/codex/chat?after=../../')).status,400);
}));
test('goal delegation is bounded, revisioned, and cannot supply shell commands',()=>withServer({createGoal:async()=>({status:'ok',result:{goalId:'goal_1'}})},async call=>{
 const body={...baseFields,goal:'Build app',instructions:'Do a test',priority:'normal'};
 assert.equal((await call('/v1/goals','POST',body)).status,200);
 assert.equal((await call('/v1/goals','POST',{...body,requestId:'req_2',shell:'powershell'})).status,400);
 assert.equal((await call('/v1/goals','POST',{...body,requestId:'req_3',priority:'critical'})).status,400);
}));
test('review decision needs native authorization and pinned revision',()=>withServer({reviews:async()=>({reviews:[{id:'rev_1'}]}),reviewDecision:async x=>({status:'ok',result:{reviewId:x.reviewId}})},async call=>{
 assert.equal((await call('/v1/reviews')).status,200);
 assert.equal((await call('/v1/reviews/rev_1/decision','POST',{...baseFields,decision:'approve_review'})).status,200);
 assert.equal((await call('/v1/reviews/rev_1/decision','POST',{...baseFields,requestId:'req_2',decision:'merge'})).status,400);
}));
test('alerts are read only, paginated and backend-gated',()=>withServer({alerts:async()=>({alerts:[{id:'a1'}]})},async call=>{
 assert.equal((await call('/v1/alerts')).status,200);
 assert.equal((await call('/v1/alerts?after=secret/path')).status,400);
 assert.equal((await call('/v1/alerts?debug=yes')).status,400);
}));
test('no desktop feature returns unavailable and demo mode blocks writes',async()=>{
 await withServer({},async call=>{
  assert.equal((await call('/v1/reviews')).status,503);
  assert.equal((await call('/v1/alerts')).status,503);
  assert.equal((await call('/v1/agents/codex/chat')).status,503);
 });
 await withServer({createGoal:async()=>({status:'ok'}),sendChat:async()=>({status:'ok'}),reviewDecision:async()=>({status:'ok'})},async call=>{
  assert.equal((await call('/v1/goals','POST',{...baseFields,goal:'Develop UI',instructions:'',priority:'normal'})).status,403);
  assert.equal((await call('/v1/agents/codex/chat','POST',{...baseFields,message:'hello'})).status,403);
  assert.equal((await call('/v1/reviews/rev_1/decision','POST',{...baseFields,decision:'approve_review'})).status,403);
 },false);
});
