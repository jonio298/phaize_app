import test from 'node:test';
import assert from 'node:assert/strict';
import {createGateway} from '../src/gateway.mjs';
const token='test-token-longer-than-thirty-two-characters';
const common={deviceId:'phone01',expectedRevision:9,requestId:'request01'};
async function gateway(adapter,run,allowControl=true){
 const server=createGateway({token,adapter,allowControl,rateLimit:200});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const base='http://127.0.0.1:'+server.address().port;
 const request=async(path,method='GET',data)=>{
  const r=await fetch(base+path,{method,headers:{authorization:'Bearer '+token,...(data?{'content-type':'application/json'}:{})},body:data?JSON.stringify(data):undefined});
  return {status:r.status,body:await r.json()};
 };
 try{await run(request);}finally{await new Promise(r=>server.close(r));}
}
const adapter=()=>({snapshot:async()=>({revision:9,timeline:[]}),decide:async()=>({status:'forbidden'})});
test('task submission is routed to desktop only',()=>gateway({...adapter(),createTask:async x=>({status:'ok',result:{taskId:'T-5',agentId:x.agentId}})},async request=>{
 const payload={...common,title:'Review build errors',description:'Check logs',agentId:'codex',priority:'high'};
 const r=await request('/v1/tasks','POST',payload);
 assert.equal(r.status,200);assert.equal(r.body.taskId,'T-5');
 assert.equal((await request('/v1/tasks','POST',{...payload,requestId:'request02',priority:'extreme'})).status,400);
 assert.equal((await request('/v1/tasks','POST',{...payload,requestId:'request03',command:'rm -rf /'})).status,400);
}));
test('tasks disabled in demo or absent native bridge',async()=>{
 await gateway({...adapter(),createTask:async()=>({status:'ok'})},async request=>{
  const r=await request('/v1/tasks','POST',{...common,title:'Test task',description:'',agentId:null,priority:'normal'});
  assert.equal(r.status,403);
 },false);
 await gateway(adapter(),async request=>{
  const r=await request('/v1/tasks','POST',{...common,title:'Test task',description:'',agentId:null,priority:'normal'});
  assert.equal(r.status,503);
 });
});
test('scoped file browser accepts only opaque IDs',()=>gateway({...adapter(),browse:async({parentId})=>({parentId,entries:[{id:'file1',name:'a.txt',kind:'file'}]})},async request=>{
 assert.equal((await request('/v1/files/browse?parent=dir_1')).status,200);
 assert.equal((await request('/v1/files/browse?parent=..%2F..%2FWindows')).status,400);
 assert.equal((await request('/v1/files/browse?parent=dir1&parent=dir2')).status,400);
}));
test('error listing supports opaque cursors and rejects extra filters',()=>gateway({...adapter(),errors:async()=>({entries:[{id:'e1',severity:'error',source:'watcher',message:'overflow'}],nextCursor:null})},async request=>{
 assert.equal((await request('/v1/errors?after=cursor_1')).status,200);
 assert.equal((await request('/v1/errors?after=../secrets')).status,400);
 assert.equal((await request('/v1/errors?raw=true')).status,400);
}));
test('no native file browser or logs means unavailable',()=>gateway(adapter(),async request=>{
 assert.equal((await request('/v1/files/browse')).status,503);
 assert.equal((await request('/v1/errors')).status,503);
}));
