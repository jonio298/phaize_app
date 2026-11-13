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
      if(!response.ok) throw Error('desktop bridge unavailable');
      const result=await response.json();
      if(!result || typeof result!=='object' || Array.isArray(result)) throw Error('invalid desktop response');
      return result;
    }finally{clearTimeout(timeout);}
  }
  return {
    async snapshot(){try{const state=await invoke('/mobile/v1/snapshot');return {...state,mode:'live'};}catch{throw Error('Desktop not available; remote state cannot be trusted');}},
    async decide(request){try{return await invoke('/mobile/v1/approvals/decisions','POST',request);}catch{return FAIL;}},
    async control(request){try{return await invoke('/mobile/v1/agent-control','POST',request);}catch{return FAIL;}}
  };
}
