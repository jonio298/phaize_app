// Desktop bridge is a constrained transport, NOT an authorization authority.
// PhAize native service must independently enforce device identity, request revisions,
// approval TTL, policy, writer leases, workspace scope and MXC containment.
const FAIL=Object.freeze({status:'unavailable'});
export function createDesktopAdapter({bridgeUrl,bridgeToken,fetchImpl=fetch}={}){
  if(!bridgeUrl || !/^http:\/\/127\.0\.0\.1:\d+$/.test(bridgeUrl)) throw Error('Desktop bridge must be IPv4 loopback HTTP; expose gateway through separate trusted HTTPS tunnel.');
  if(typeof bridgeToken!=='string'||bridgeToken.length<32) throw Error('Desktop bridge secret required');
  async function invoke(path,method='GET',payload){
    const controller=new AbortController(); const timeout=setTimeout(()=>controller.abort(),7500);
    try{
      const response=await fetchImpl(bridgeUrl+path,{method,signal:controller.signal,headers:{'authorization':'Bearer '+bridgeToken,'content-type':'application/json'},body:payload?JSON.stringify(payload):undefined,redirect:'error'});
      const result=await response.json();
      if(!response.ok && !(response.status===403 && result?.status==='forbidden')) throw Error('desktop bridge unavailable');
      if(!result || typeof result!=='object' || Array.isArray(result)) throw Error('invalid desktop response');
      return result;
    }finally{clearTimeout(timeout);}
  }
  return {
    async snapshot(){try{const state=await invoke('/mobile/v1/snapshot');if(state.schemaVersion!==1 || state.mode!=='live' || !state.host || !Array.isArray(state.agents) || !Array.isArray(state.files) || !Array.isArray(state.timeline) || !Array.isArray(state.approvals) || !Array.isArray(state.tasks)) throw Error('invalid desktop snapshot');return state;}catch{throw Error('Desktop not available; remote state cannot be trusted');}},
    // Read-only conversation views. Anything unexpected fails closed; the desktop must redact secrets before sending.
    async chats(){try{const r=await invoke('/mobile/v1/chats');if(!Array.isArray(r.chats))throw Error('invalid desktop chats');return r.chats;}catch{throw Error('Desktop not available; remote chats cannot be trusted');}},
    async chat(id){try{if(!/^[a-zA-Z0-9_-]{1,80}$/.test(id))throw Error('invalid chat id');const r=await invoke('/mobile/v1/chats/'+id);if(!Array.isArray(r.messages))throw Error('invalid desktop chat');return r;}catch{throw Error('Desktop not available; remote chats cannot be trusted');}},
    async decide(request){try{return await invoke('/mobile/v1/approvals/decisions','POST',request);}catch{return FAIL;}},
    async control(request){try{return await invoke('/mobile/v1/agent-control','POST',request);}catch{return FAIL;}}
  };
}
