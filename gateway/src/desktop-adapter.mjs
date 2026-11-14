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
    async control(request){try{return await invoke('/mobile/v1/agent-control','POST',request);}catch{return FAIL;}},
    async permissions(request){try{return await invoke('/mobile/v1/agent-permissions','POST',request);}catch{return FAIL;}},
    async createTask(request){try{return await invoke('/mobile/v1/tasks','POST',request);}catch{return FAIL;}},
    async browse({parentId}){try{return await invoke('/mobile/v1/files/browse'+(parentId?'?parent='+encodeURIComponent(parentId):''));}catch{return FAIL;}},
    async errors({after}){try{return await invoke('/mobile/v1/errors'+(after?'?after='+encodeURIComponent(after):''));}catch{return FAIL;}},
    async chat({agentId,after}){try{return await invoke('/mobile/v1/agents/'+encodeURIComponent(agentId)+'/chat'+(after?'?after='+encodeURIComponent(after):''));}catch{return FAIL;}},
    async reviews({after}){try{return await invoke('/mobile/v1/reviews'+(after?'?after='+encodeURIComponent(after):''));}catch{return FAIL;}},
    async alerts({after}){try{return await invoke('/mobile/v1/alerts'+(after?'?after='+encodeURIComponent(after):''));}catch{return FAIL;}},
    async sendChat(request){try{return await invoke('/mobile/v1/agent-chat','POST',request);}catch{return FAIL;}},
    async createGoal(request){try{return await invoke('/mobile/v1/goals','POST',request);}catch{return FAIL;}},
    async reviewDecision(request){try{return await invoke('/mobile/v1/reviews/decision','POST',request);}catch{return FAIL;}},
    async briefing(){try{return await invoke('/mobile/v1/briefing');}catch{return FAIL;}},
    async builds(){try{return await invoke('/mobile/v1/builds');}catch{return FAIL;}},
    async ops(){try{return await invoke('/mobile/v1/ops');}catch{return FAIL;}},
    async opsAction(request){try{return await invoke('/mobile/v1/ops/action','POST',request);}catch{return FAIL;}}

  };
}
