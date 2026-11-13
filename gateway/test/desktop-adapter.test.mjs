import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {createDesktopAdapter} from '../src/desktop-adapter.mjs';
import {createGateway} from '../src/gateway.mjs';

const bridgeSecret='desktop-super-secret-0123456789-abcdefgh';
const gatewaySecret='gateway-super-secret-0123456789-abcdefgh';
async function withBridge(handler,exercise) {
 const server=http.createServer(handler);
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try {return await exercise('http://127.0.0.1:'+server.address().port);}
 finally{await new Promise(resolve=>server.close(resolve));}
}
test('native snapshot flows through gateway as live but read-only',async()=>{
 await withBridge((req,res)=>{
  assert.equal(req.headers.authorization,'Bearer '+bridgeSecret);
  if(req.url!=='/mobile/v1/snapshot'){res.writeHead(403);res.end(JSON.stringify({status:'forbidden'}));return;}
  res.writeHead(200,{'content-type':'application/json'});
  res.end(JSON.stringify({schemaVersion:1,revision:4,mode:'live',host:{name:'PhAize',online:true,monitoring:'not_attested_by_bridge',containment:'not_attested_by_bridge',cpu:0,memoryPercent:0},agents:[{id:'session-1',name:'Codex',status:'working',role:'Observed desktop session',task:null,cpu:0,memoryMb:0}],approvals:[],approvalsAvailability:'not_connected',controlsAvailability:'not_connected',tasks:[],tasksAvailability:'not_connected',files:[],timeline:[],statistics:{eventsToday:0,blockedToday:0,completedToday:0}}));
 },async url=>{
  const adapter=createDesktopAdapter({bridgeUrl:url,bridgeToken:bridgeSecret});
  const gateway=createGateway({adapter,token:gatewaySecret,allowControl:true});
  await new Promise(resolve=>gateway.listen(0,'127.0.0.1',resolve));
  try{
   const base='http://127.0.0.1:'+gateway.address().port;
   const headers={Authorization:'Bearer '+gatewaySecret,'content-type':'application/json'};
   const response=await fetch(base+'/v1/snapshot',{headers});
   assert.equal(response.status,200);
   const state=await response.json();
   assert.equal(state.agents[0].name,'Codex');
   assert.equal(state.mode,'live');
   assert.equal(state.approvalsAvailability,'not_connected');
   const denied=await fetch(base+'/v1/approvals/123/decisions',{method:'POST',headers,body:JSON.stringify({decision:'approve_once',deviceId:'phone1',expectedRevision:1,requestId:'unique123'})});
   assert.equal(denied.status,403);
   const stop=await fetch(base+'/v1/agents/session-1/control',{method:'POST',headers,body:JSON.stringify({action:'emergency_stop',deviceId:'phone1',expectedRevision:4,requestId:'unique124'})});
   assert.equal(stop.status,403);
  }finally{await new Promise(resolve=>gateway.close(resolve));}
 });
});
test('desktop offline must fail closed',async()=>{
 const adapter=createDesktopAdapter({bridgeUrl:'http://127.0.0.1:8790',bridgeToken:bridgeSecret,fetchImpl:async()=>{throw Error('offline')}});
 await assert.rejects(adapter.snapshot());
 assert.deepEqual(await adapter.decide({}),{status:'unavailable'});
 assert.deepEqual(await adapter.control({}),{status:'unavailable'});
});
