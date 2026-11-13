import test from 'node:test';import assert from 'node:assert/strict';
import {createGateway} from '../src/gateway.mjs';
import {createDemoAdapter} from '../src/demo-adapter.mjs';
import {createDesktopAdapter} from '../src/desktop-adapter.mjs';
const TOKEN='test-secret-32-characters-minimum-key';
const headers={'authorization':'Bearer '+TOKEN,'content-type':'application/json'};
async function serve(adapter,cb){
 const s=createGateway({adapter,token:TOKEN,rateLimit:500});
 await new Promise(r=>s.listen(0,'127.0.0.1',r));
 try{await cb('http://127.0.0.1:'+s.address().port);}finally{await new Promise(r=>s.close(r));}
}
const withDemo=cb=>serve(createDemoAdapter(),cb);
const base=()=>({async snapshot(){return {};},async decide(){return {status:'unavailable'};}});

test('chats require authentication',()=>withDemo(async b=>{
 assert.equal((await fetch(b+'/v1/chats')).status,401);
 assert.equal((await fetch(b+'/v1/chats/chat-codex')).status,401);
}));

test('chat list has only the documented fields',()=>withDemo(async b=>{
 const j=await (await fetch(b+'/v1/chats',{headers})).json();
 assert.equal(j.availability,'connected');
 assert.equal(j.chats.length,3);
 for(const c of j.chats)assert.deepEqual(Object.keys(c).sort(),['agentId','id','messageCount','preview','title','updatedAt']);
 assert.ok(j.chats[0].messageCount>=1);
}));

test('a chat returns its messages with roles and no extra fields',()=>withDemo(async b=>{
 const j=await (await fetch(b+'/v1/chats/chat-codex',{headers})).json();
 assert.equal(j.id,'chat-codex');
 assert.ok(j.messages.length>=4);
 for(const m of j.messages){assert.deepEqual(Object.keys(m).sort(),['id','role','speaker','text','timestamp']);assert.ok(['user','agent','system'].includes(m.role));}
 assert.ok(j.messages.some(m=>m.text.includes('```')),'the sample includes a code block');
}));

test('unknown and malformed chat ids are 404 and never reach the adapter with odd input',async()=>{
 const seen=[];
 await serve({...base(),async chats(){return [];},async chat(id){seen.push(id);return null;}},async b=>{
  assert.equal((await fetch(b+'/v1/chats/nope',{headers})).status,404);
  assert.equal((await fetch(b+'/v1/chats/..%2Fsecret',{headers})).status,404);
  assert.equal((await fetch(b+'/v1/chats/a/b',{headers})).status,404);
  assert.equal((await fetch(b+'/v1/chats/'+'x'.repeat(81),{headers})).status,404);
 });
 assert.deepEqual(seen,['nope'],'only a well-formed id was passed on');
});

test('chats are read only: no write methods exist',()=>withDemo(async b=>{
 for(const method of ['POST','PUT','PATCH','DELETE']){
  assert.equal((await fetch(b+'/v1/chats',{method,headers,body:'{}'})).status,404,method);
  assert.equal((await fetch(b+'/v1/chats/chat-codex',{method,headers,body:'{}'})).status,404,method);
 }
}));

test('output is whitelisted, bounded and role-checked even if the adapter is careless',async()=>{
 const messages=Array.from({length:300},(_,i)=>({id:'m'+i,role:i===0?'admin':'agent',speaker:'S',text:'x'.repeat(5000),timestamp:'t',secret:'leak',extra:{a:1}}));
 const many=Array.from({length:150},(_,i)=>({id:'c'+i,title:'T',agentId:'a',preview:'p'.repeat(500),updatedAt:'u',messageCount:-5,token:'leak'}));
 many.push({id:'bad id with spaces',title:'x'});
 await serve({...base(),async chats(){return many;},async chat(){return {id:'c1',title:'T',agentId:'a',messages,apiKey:'leak'};}},async b=>{
  const list=await (await fetch(b+'/v1/chats',{headers})).json();
  assert.equal(list.chats.length,100);
  assert.ok(list.chats.every(c=>c.preview.length<=200&&c.messageCount===0&&!('token' in c)));
  assert.ok(list.chats.every(c=>/^[a-zA-Z0-9][a-zA-Z0-9_-]*$/.test(c.id)),'ids that fail the id rule are dropped');
  const one=await (await fetch(b+'/v1/chats/c1',{headers})).json();
  assert.equal(one.messages.length,200);
  assert.ok(one.messages.every(m=>m.text.length===4000&&!('secret' in m)&&!('extra' in m)));
  assert.ok(one.messages.every(m=>['user','agent','system'].includes(m.role)));
  assert.equal('apiKey' in one,false);
 });
});

test('an adapter without chats reports not connected instead of failing',()=>serve(base(),async b=>{
 const j=await (await fetch(b+'/v1/chats',{headers})).json();
 assert.deepEqual(j,{availability:'not_connected',chats:[]});
 assert.equal((await fetch(b+'/v1/chats/chat-codex',{headers})).status,503);
}));

test('the desktop bridge fails closed for chats',async()=>{
 const down=createDesktopAdapter({bridgeUrl:'http://127.0.0.1:8790',bridgeToken:TOKEN,fetchImpl:async()=>{throw Error('offline');}});
 await assert.rejects(()=>down.chats(),/cannot be trusted/);
 await assert.rejects(()=>down.chat('chat-1'),/cannot be trusted/);
 await assert.rejects(()=>down.chat('../x'),/cannot be trusted/);
 const bad=createDesktopAdapter({bridgeUrl:'http://127.0.0.1:8790',bridgeToken:TOKEN,fetchImpl:async()=>({ok:true,status:200,json:async()=>({chats:'nope',messages:'nope'})})});
 await assert.rejects(()=>bad.chats(),/cannot be trusted/);
 await assert.rejects(()=>bad.chat('chat-1'),/cannot be trusted/);
 const good=createDesktopAdapter({bridgeUrl:'http://127.0.0.1:8790',bridgeToken:TOKEN,fetchImpl:async url=>({ok:true,status:200,json:async()=>String(url).endsWith('/chats')?{chats:[{id:'c1'}]}:{id:'c1',messages:[]}})});
 assert.deepEqual(await good.chats(),[{id:'c1'}]);
 assert.deepEqual(await good.chat('c1'),{id:'c1',messages:[]});
});

test('a bridge failure surfaces as 503 through the gateway, never as fake chats',async()=>{
 const down=createDesktopAdapter({bridgeUrl:'http://127.0.0.1:8790',bridgeToken:TOKEN,fetchImpl:async()=>{throw Error('offline');}});
 await serve({...base(),chats:()=>down.chats(),chat:id=>down.chat(id)},async b=>{
  assert.equal((await fetch(b+'/v1/chats',{headers})).status,503);
  assert.equal((await fetch(b+'/v1/chats/chat-1',{headers})).status,503);
 });
});
