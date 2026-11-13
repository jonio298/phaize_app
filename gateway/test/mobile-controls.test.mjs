import test from 'node:test';
import assert from 'node:assert/strict';
import {createGateway} from '../src/gateway.mjs';

const token='test-token-with-at-least-thirty-two-characters';
async function withGateway(adapter,fn,allowControl=true){
 const server=createGateway({adapter,token,allowControl});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{
  const base='http://127.0.0.1:'+server.address().port;
  const post=async(path,body)=>{const r=await fetch(base+path,{method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},body:JSON.stringify(body)});return {status:r.status,body:await r.json()};};
  await fn(post);
 } finally {await new Promise(resolve=>server.close(resolve));}
}
const common={deviceId:'phone_1',expectedRevision:3,requestId:'req_1'};
const snapshot=async()=>({revision:3,timeline:[]});
test('start is native-gated and forwarded with agent identity',async()=>{
 let called;
 await withGateway({snapshot,decide:async()=>({status:'forbidden'}),control:async x=>{called=x;return {status:'ok',result:{applied:true}};}},async post=>{
  const r=await post('/v1/agents/codex/control',{...common,action:'start'});
  assert.equal(r.status,200);assert.equal(r.body.applied,true);
  assert.deepEqual(called,{...common,action:'start',agentId:'codex'});
 });
});
test('permission policy rejects auto-write and passes safe policy to desktop',async()=>{
 let called;
 await withGateway({snapshot,decide:async()=>({status:'forbidden'}),permissions:async x=>{called=x;return {status:'ok',result:{applied:true}};}},async post=>{
  const path='/v1/agents/codex/permissions';
  assert.equal((await post(path,{...common,read:'allow',write:'allow',delete:'ask'})).status,400);
  assert.equal((await post(path,{...common,read:'allow',write:'ask',delete:'allow'})).status,400);
  const valid={...common,read:'allow',write:'ask',delete:'deny'};
  assert.equal((await post(path,valid)).status,200);
  assert.deepEqual(called,{...valid,agentId:'codex'});
 });
});
test('missing native permission handler fails closed',async()=>{
 await withGateway({snapshot,decide:async()=>({status:'forbidden'}),control:async()=>({status:'ok'})},async post=>{
  const r=await post('/v1/agents/codex/permissions',{...common,read:'allow',write:'ask',delete:'ask'});
  assert.equal(r.status,503);assert.equal(r.body.error,'native_permissions_unavailable');
 });
});
test('demo mode disables agent control and permission edits',async()=>{
 await withGateway({snapshot,decide:async()=>({status:'forbidden'}),control:async()=>({status:'ok'}),permissions:async()=>({status:'ok'})},async post=>{
  assert.equal((await post('/v1/agents/codex/control',{...common,action:'start'})).status,403);
  assert.equal((await post('/v1/agents/codex/permissions',{...common,read:'allow',write:'ask',delete:'ask'})).status,403);
 },false);
});
