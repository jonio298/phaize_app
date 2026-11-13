import test from 'node:test';
import assert from 'node:assert/strict';
import {createGateway} from '../src/gateway.mjs';

const token='a-random-test-token-longer-than-32-characters';
const baseData={deviceId:'phone01',expectedRevision:7,requestId:'req001'};
async function withServer(adapter,run,allowControl=true){
 const server=createGateway({adapter,token,allowControl,rateLimit:200});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const base='http://127.0.0.1:'+server.address().port;
 const request=async(path,body)=>{const r=await fetch(base+path,{method:body?'POST':'GET',headers:{authorization:'Bearer '+token,...(body?{'content-type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});return {status:r.status,body:await r.json()};};
 try{await run(request)}finally{await new Promise(resolve=>server.close(resolve))}
}
const minimum=()=>({snapshot:async()=>({revision:7,timeline:[]}),decide:async()=>({status:'forbidden'})});
test('remote console read requires native handler and rejects parameters',async()=>{
 await withServer(minimum(),async request=>assert.equal((await request('/v1/remote/console')).status,503));
 await withServer({...minimum(),remoteStatus:async()=>({revision:7,commands:[{id:'tests',label:'Run tests'}],locked:false})},async request=>{
  assert.equal((await request('/v1/remote/console')).status,200);
  assert.equal((await request('/v1/remote/console?path=/Windows')).status,400);
 });
});
test('remote action rejects unlisted and unsafe command shapes',()=>withServer({...minimum(),remoteAction:async()=>({status:'ok',result:{applied:true}})},async request=>{
 const route='/v1/remote/actions';
 assert.equal((await request(route,{...baseData,action:'execute',command:'del C:\\'})).status,400);
 assert.equal((await request(route,{...baseData,action:'run_command',commandId:'test',shell:'powershell'})).status,400);
 assert.equal((await request(route,{...baseData,action:'run_command',commandId:'../../secret'})).status,400);
 assert.equal((await request(route,{...baseData,action:'set_limits',agentId:'codex',cpuPercent:150,memoryMb:2048,networkMode:'standard'})).status,400);
 assert.equal((await request(route,{...baseData,action:'lockdown',reason:'x'})).status,400);
}));
test('valid bounded commands go only to native adapter',()=>withServer({...minimum(),remoteAction:async x=>({status:'ok',result:{accepted:true,action:x.action}})},async request=>{
 for(const [i,body] of [
  {action:'run_command',commandId:'catalog_tests'},
  {action:'restore_checkpoint',checkpointId:'checkpoint_2'},
  {action:'set_limits',agentId:'codex',cpuPercent:35,memoryMb:2048,networkMode:'restricted'},
  {action:'capture_preview'},
  {action:'refresh_git'},
  {action:'lockdown',reason:'Manual remote security lockdown'}
 ].entries()){
  const r=await request('/v1/remote/actions',{...baseData,requestId:'req'+i,...body});
  assert.equal(r.status,200);assert.equal(r.body.action,body.action);
 }
}));
test('critical remote requests disabled in demo and without native authority',async()=>{
 const payload={...baseData,action:'lockdown',reason:'Manual remote security lockdown'};
 await withServer({...minimum(),remoteAction:async()=>({status:'ok'})},async request=>{
  assert.equal((await request('/v1/remote/actions',payload)).status,403);
 },false);
 await withServer(minimum(),async request=>{
  assert.equal((await request('/v1/remote/actions',payload)).status,503);
 });
});
test('replay of same request ID with different action is rejected',()=>withServer({...minimum(),remoteAction:async()=>({status:'ok',result:{applied:true}})},async request=>{
 assert.equal((await request('/v1/remote/actions',{...baseData,action:'refresh_git'})).status,200);
 assert.equal((await request('/v1/remote/actions',{...baseData,action:'capture_preview'})).status,409);
}));
