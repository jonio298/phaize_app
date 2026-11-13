import test from 'node:test';import assert from 'node:assert/strict';
import {createGateway} from '../src/gateway.mjs';
import {createDemoAdapter} from '../src/demo-adapter.mjs';
import {createDesktopAdapter} from '../src/desktop-adapter.mjs';
const TOKEN='test-secret-32-characters-minimum-key';
async function withServer(cb,allowControl=false){
 const s=createGateway({adapter:createDemoAdapter(),token:TOKEN,rateLimit:200,allowControl});
 await new Promise(r=>s.listen(0,'127.0.0.1',r));
 try{await cb('http://127.0.0.1:'+s.address().port);}
 finally{await new Promise(r=>s.close(r));}
}
const headers={'authorization':'Bearer '+TOKEN,'content-type':'application/json'};
const body=(x={})=>({decision:'approve_once',deviceId:'phone1',expectedRevision:1,requestId:'req1',...x});
const post=(url,data)=>fetch(url,{method:'POST',headers,body:JSON.stringify(data)});
test('token length required',()=>assert.throws(()=>createGateway({adapter:createDemoAdapter(),token:'bad'})));
test('health',()=>withServer(async b=>assert.equal((await fetch(b+'/health')).status,200)));
test('authentication required',()=>withServer(async b=>assert.equal((await fetch(b+'/v1/snapshot')).status,401)));
test('demo snapshot explicitly marked',()=>withServer(async b=>{const r=await fetch(b+'/v1/snapshot',{headers});assert.equal((await r.json()).mode,'demo');}));
test('events revisions',()=>withServer(async b=>{const j=await (await fetch(b+'/v1/events?after=0',{headers})).json();assert.equal(j.events.length,1);}));
test('invalid event cursor',()=>withServer(async b=>assert.equal((await fetch(b+'/v1/events?after=-1',{headers})).status,400)));
test('approval only once',()=>withServer(async b=>assert.equal((await post(b+'/v1/approvals/ap-100/decisions',body())).status,200)));
test('idempotent approval',()=>withServer(async b=>{const u=b+'/v1/approvals/ap-100/decisions';const a=await(await post(u,body())).json();const z=await(await post(u,body())).json();assert.deepEqual(a,z);}));
test('idempotency conflicts for another target',()=>withServer(async b=>{await post(b+'/v1/approvals/ap-100/decisions',body());assert.equal((await post(b+'/v1/approvals/ap-101/decisions',body())).status,409);}));
test('reject forbidden approve always',()=>withServer(async b=>assert.equal((await post(b+'/v1/approvals/ap-100/decisions',body({decision:'approve_always'}))).status,400)));
test('reject unexpected fields',()=>withServer(async b=>assert.equal((await post(b+'/v1/approvals/ap-100/decisions',{...body(),scope:'all'})).status,400)));
test('stale revisions conflict',()=>withServer(async b=>assert.equal((await post(b+'/v1/approvals/ap-100/decisions',body({expectedRevision:99}))).status,409)));
test('controls disabled in demo',()=>withServer(async b=>assert.equal((await post(b+'/v1/agents/codex/control',{action:'pause',deviceId:'phone1',expectedRevision:1,requestId:'r2'})).status,403)));
test('no unknown endpoints',()=>withServer(async b=>assert.equal((await fetch(b+'/v1/files/raw',{headers})).status,404)));
test('bridge enforces loopback',()=>assert.throws(()=>createDesktopAdapter({bridgeUrl:'https://example.com',bridgeToken:TOKEN})));
test('bridge fails closed',async()=>{const a=createDesktopAdapter({bridgeUrl:'http://127.0.0.1:8790',bridgeToken:TOKEN,fetchImpl:async()=>{throw Error('offline');}});assert.deepEqual(await a.decide({}),{status:'unavailable'});await assert.rejects(()=>a.snapshot());});
