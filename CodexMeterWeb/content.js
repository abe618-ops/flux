
const CM = (() => {
  const day = d => d.toISOString().slice(0,10);
  const addDays = (s,n) => {
    const d=new Date(s+"T12:00:00");
    d.setDate(d.getDate()+n);
    return day(d);
  };

  async function getAccessToken(){
    try{
      const el=document.getElementById("client-bootstrap");
      if(el && el.textContent){
        const j=JSON.parse(el.textContent);
        const t=(j.session&&j.session.accessToken)||(j.session&&j.session.access_token);
        if(t) return t;
      }
    }catch(e){}
    for(const p of ["/api/auth/session","/backend-api/auth/session"]){
      try{
        const r=await fetch(p,{credentials:"include",cache:"no-store"});
        if(!r.ok) continue;
        const j=await r.json();
        const t=j.accessToken||j.access_token||(j.session&&j.session.accessToken);
        if(t) return t;
      }catch(e){}
    }
    return null;
  }

  async function api(path, token, soft){
    try{
      const headers={Accept:"application/json"};
      if(token) headers.Authorization="Bearer "+token;
      const r=await fetch(path,{credentials:"include",cache:"no-store",headers});
      if(!r.ok){
        if(soft) return null;
        throw new Error("HTTP "+r.status+" · "+path);
      }
      return await r.json();
    }catch(e){
      if(soft) return null;
      throw e;
    }
  }

  function normWindow(w){
    if(!w || !Number.isFinite(Number(w.used_percent))) return null;
    const used=Math.max(0,Math.min(100,Number(w.used_percent)));
    const seconds=Number(w.limit_window_seconds)||0;
    const resetAt=Number(w.reset_at)
      ? Number(w.reset_at)*1000
      : Number(w.reset_after_seconds)
        ? Date.now()+Number(w.reset_after_seconds)*1000
        : null;
    return {used,remaining:100-used,seconds,resetAt};
  }

  function pickWindows(u){
    const list=[];
    const rl=(u&&u.rate_limit)||{};
    [rl.primary_window,rl.secondary_window].filter(Boolean).forEach(x=>{
      const w=normWindow(x); if(w) list.push(w);
    });
    for(const extra of ((u&&u.additional_rate_limits)||[])){
      const x=(extra&&extra.rate_limit)||{};
      [x.primary_window,x.secondary_window].filter(Boolean).forEach(raw=>{
        const w=normWindow(raw);
        if(w){w.name=extra.limit_name||extra.metered_feature||"additional";list.push(w);}
      });
    }
    const exact=s=>list.find(x=>x.seconds===s);
    const near=s=>[...list].sort((a,b)=>Math.abs(a.seconds-s)-Math.abs(b.seconds-s))[0]||null;
    return {five:exact(18000)||near(18000),weekly:exact(604800)||near(604800)};
  }

  function parseAnalytics(counts, breakdown){
    const byDate=new Map((((breakdown&&breakdown.data)||[]).map(x=>[x.date,x])));
    const days=[], models=new Map();
    for(const row of ((counts&&counts.data)||[])){
      const totals=row.totals||{};
      const bd=byDate.get(row.date)||{};
      let uncached=Number(totals.uncached_text_input_tokens)||0;
      let cached=Number(totals.cached_text_input_tokens)||0;
      let output=Number(totals.text_output_tokens)||0;
      let total=Number(totals.text_total_tokens)||uncached+cached+output;

      if(Array.isArray(bd.models) && bd.models.length){
        uncached=cached=output=total=0;
        for(const m of bd.models){
          const u=Number(m.uncached_text_input_tokens)||0;
          const c=Number(m.cached_text_input_tokens)||0;
          const o=Number(m.text_output_tokens)||0;
          const t=Number(m.text_total_tokens)||u+c+o;
          uncached+=u;cached+=c;output+=o;total+=t;
          const key=(m.model||"Unknown")+((m.speed&&m.speed!=="standard")?" · "+m.speed:"");
          models.set(key,(models.get(key)||0)+t);
        }
      }
      days.push({
        date:row.date,total,uncached,cached,output,
        turns:Number(totals.turns)||0,
        threads:Number(totals.threads)||0
      });
    }
    days.sort((a,b)=>a.date.localeCompare(b.date));
    return {
      days,
      models:[...models].map(x=>({model:x[0],tokens:x[1]})).sort((a,b)=>b.tokens-a.tokens)
    };
  }

  async function load(){
    const token=await getAccessToken();
    if(!token) throw new Error("未检测到 ChatGPT 登录会话");

    const usage=await api("/backend-api/wham/usage",token,false);
    const end=day(new Date());
    const start=addDays(end,-29);
    const endExclusive=addDays(end,1);
    const range="start_date="+start+"&end_date="+endExclusive+"&group_by=day";

    const results=await Promise.all([
      api("/backend-api/wham/analytics/daily-workspace-usage-counts?"+range+"&workspace_user=true",token,true),
      api("/backend-api/wham/usage/daily-workspace-user-token-usage-breakdown?"+range,token,true),
      api("/backend-api/wham/usage/daily-token-usage-breakdown?"+range,token,true)
    ]);
    const counts=results[0], breakdown=results[1], percent=results[2];
    const w=pickWindows(usage);
    return {
      ok:true,
      plan:usage.plan_type||"ChatGPT",
      fetchedAt:Date.now(),
      five:w.five,
      weekly:w.weekly,
      credits:{
        balance:(usage.credits && Number.isFinite(Number(usage.credits.balance)))?Number(usage.credits.balance):null,
        resetCredits:Number(usage.rate_limit_reset_credits&&usage.rate_limit_reset_credits.available_count)||0
      },
      analytics:parseAnalytics(counts,breakdown),
      diagnostics:{
        quota:Boolean(usage),
        dailyCounts:Boolean(counts),
        tokenBreakdown:Boolean(breakdown),
        dailyPercent:Boolean(percent)
      }
    };
  }
  return {load};
})();

chrome.runtime.onMessage.addListener((msg,_sender,sendResponse)=>{
  if(!msg || msg.type!=="CODEXMETER_FETCH") return false;
  CM.load().then(sendResponse).catch(e=>sendResponse({ok:false,error:e.message||String(e)}));
  return true;
});
