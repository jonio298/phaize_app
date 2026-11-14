import test from 'node:test';
import assert from 'node:assert/strict';
import {createGateway} from '../src/gateway.mjs';

const token='briefing-integration-test-token-123456789';
async function run(adapter,fn){
 const server=createGateway({adapter,token,rateLimit:100,allowControl:false});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{
  const base='http://127.0.0.1:'+server.address().port;
  const get=async path=>{
   const r=await fetch(base+path,{headers:{authorization:'Bearer '+token}});
   return {status:r.status,data:await r.json()};
  };
  await fn(get);
 }finally{await new Promise(resolve=>server.close(resolve));}
}
const adapter={snapshot:async()=>({mode:'live',revision:2}),decide:async()=>({status:'forbidden'})};
test('briefing and builds unavailable when desktop does not implement them',()=>run(adapter,async get=>{
 const briefing=await get('/v1/briefing');
 const builds=await get('/v1/builds');
 assert.equal(briefing.status,503);
 assert.equal(builds.status,503);
}));
test('briefing and build feed read only with bounded arrays',()=>run({
 ...adapter,
 briefing:async()=>({summary:'Tasks completed',filesChanged:12,completed:Array(80).fill('task'),blockers:[],decisions:[]}),
 builds:async()=>({repository:'jonio298/phaize',runs:Array(100).fill({id:'run1',conclusion:'success'})})
},async get=>{
 const briefing=await get('/v1/briefing');
 assert.equal(briefing.status,200);
 assert.equal(briefing.data.completed.length,30);
 assert.equal(briefing.data.filesChanged,12);
 const builds=await get('/v1/builds');
 assert.equal(builds.status,200);
 assert.equal(builds.data.runs.length,50);
 assert.equal((await get('/v1/builds?repo=other')).status,400);
 assert.equal((await get('/v1/briefing?since=1')).status,400);
}));
test('briefing requires authentication',async()=>{
 const server=createGateway({adapter,token});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{
  const response=await fetch('http://127.0.0.1:'+server.address().port+'/v1/briefing');
  assert.equal(response.status,401);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
