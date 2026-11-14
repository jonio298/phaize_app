import test from 'node:test';
import assert from 'node:assert/strict';
import {createGateway} from '../src/gateway.mjs';
const token='a-long-development-only-test-key-123456789';
const baseFields={deviceId:'phone_1',expectedRevision:5,requestId:'request_1'};
async function serve(adapter,callback,allowControl=true){
 const server=createGateway({adapter:{snapshot:async()=>({revision:5,timeline:[]}),decide:async()=>({status:'forbidden'}),...adapter},token,allowControl,rateLimit:200});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{
  const base='http://127.0.0.1:'+server.address().port;
  async function request(path,body){
   const response=await fetch(base+path,{method:body?'POST':'GET',headers:{authorization:'Bearer '+token,...(body?{'content-type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});
   return {status:response.status,data:await response.json()};
  }
  await callback(request);
 }finally{await new Promise(resolve=>server.close(resolve));}
}
test('operations endpoint requires native adapter and no query expansion',()=>serve({},async request=>{
 assert.equal((await request('/v1/ops')).status,503);
}));
test('operations results are bounded',()=>serve({ops:async()=>({health:{cpuPercent:12},git:{branch:'main'},checkpoints:Array.from({length:80},(_,id)=>({id})),captures:[],terminalHistory:[]})},async request=>{
 const result=await request('/v1/ops');
 assert.equal(result.status,200);assert.equal(result.data.checkpoints.length,30);
 assert.equal((await request('/v1/ops?path=/etc')).status,400);
}));
test('only allowlisted registered commands reach native bridge',()=>serve({opsAction:async req=>({status:'ok',result:{action:req.action,commandId:req.commandId}})},async request=>{
 const good={...baseFields,action:'terminal_preset',agentId:'codex',commandId:'git_status'};
 assert.equal((await request('/v1/ops/action',good)).status,200);
 assert.equal((await request('/v1/ops/action',{...good,commandId:'powershell',requestId:'request_2'})).status,400);
 assert.equal((await request('/v1/ops/action',{...good,command:'rm -rf /',requestId:'request_3'})).status,400);
}));
test('resource bounds are validated',()=>serve({opsAction:async()=>({status:'ok',result:{applied:true}})},async request=>{
 const body={...baseFields,action:'limits',agentId:'codex',cpuPercent:50,memoryMb:2048,network:'restricted'};
 assert.equal((await request('/v1/ops/action',body)).status,200);
 assert.equal((await request('/v1/ops/action',{...body,cpuPercent:120,requestId:'request_2'})).status,400);
 assert.equal((await request('/v1/ops/action',{...body,network:'unrestricted',requestId:'request_3'})).status,400);
}));
test('lockdown and checkpoint restore require constrained payloads and audit reason',()=>serve({opsAction:async()=>({status:'forbidden'})},async request=>{
 assert.equal((await request('/v1/ops/action',{...baseFields,action:'lockdown',reason:'short'})).status,400);
 assert.equal((await request('/v1/ops/action',{...baseFields,action:'lockdown',reason:'Security incident requires lockdown'})).status,403);
 assert.equal((await request('/v1/ops/action',{...baseFields,action:'checkpoint_restore',checkpointId:'../outside'})).status,400);
 assert.equal((await request('/v1/ops/action',{...baseFields,action:'capture',target:'entire_desktop'})).status,400);
}));
test('demo and missing native backend both fail closed',async()=>{
 await serve({opsAction:async()=>({status:'ok'})},async request=>{
  assert.equal((await request('/v1/ops/action',{...baseFields,action:'capture',target:'phaize_window'})).status,403);
 },false);
 await serve({},async request=>{
  assert.equal((await request('/v1/ops/action',{...baseFields,action:'capture',target:'phaize_window'})).status,503);
 });
});
