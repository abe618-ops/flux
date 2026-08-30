
const $=id=>document.getElementById(id);
function fmt(n){n=Number(n||0);if(n>=1e9)return(n/1e9).toFixed(2)+"B";if(n>=1e6)return(n/1e6).toFixed(1)+"M";if(n>=1e3)return(n/1e3).toFixed(1)+"K";return Math.round(n).toLocaleString()}
function resetText(ms){if(!ms)return"重置时间不可用";let s=Math.max(0,Math.floor((ms-Date.now())/1000));const d=Math.floor(s/86400);s%=86400;const h=Math.floor(s/3600);s%=3600;const m=Math.floor(s/60);return(d?d+"天 ":"")+(h?h+"小时 ":"")+m+"分后重置"}
function sum(days,n,key){key=key||"total";const cut=new Date();cut.setHours(0,0,0,0);cut.setDate(cut.getDate()-(n-1));return(days||[]).filter(x=>new Date(x.date+"T00:00:00")>=cut).reduce((a,x)=>a+Number(x[key]||0),0)}
function showMessage(text){$("message").textContent=text;$("message").classList.toggle("hidden",!text)}
function esc(s){return String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c]))}

function render(d){
  const five=d.five,week=d.week,days=(d.analytics&&d.analytics.days)||[],models=(d.analytics&&d.analytics.models)||[];
  $("fiveLeft").textContent=five?Math.round(five.remaining)+"%":"--%";
  $("fiveUsed").textContent=five?Math.round(five.used)+"% 已用":"--";
  $("fiveBar").style.width=(five?five.remaining:0)+"%";
  $("fiveReset").textContent=resetText(five&&five.resetAt);

  $("weekLeft").textContent=week?Math.round(week.remaining)+"%":"--%";
  $("weekUsed").textContent=week?Math.round(week.used)+"% 已用":"--";
  $("weekBar").style.width=(week?week.remaining:0)+"%";
  $("weekReset").textContent=resetText(week&&week.resetAt);

  $("today").textContent=days.length?fmt(sum(days,1)):"--";
  $("seven").textContent=days.length?fmt(sum(days,7)):"--";
  $("thirty").textContent=days.length?fmt(sum(days,30)):"--";
  $("credits").textContent=(d.credits&&d.credits.balance!=null)?String(d.credits.balance):"--";

  $("uncached").textContent=days.length?fmt(sum(days,30,"uncached")):"--";
  $("cached").textContent=days.length?fmt(sum(days,30,"cached")):"--";
  $("output").textContent=days.length?fmt(sum(days,30,"output")):"--";

  if(models.length){
    const max=Math.max(...models.map(x=>x.tokens),1);
    $("models").innerHTML=models.slice(0,12).map(x=>'<div class="modelrow"><span>'+esc(x.model)+'</span><div class="modelbar"><i style="width:'+(x.tokens/max*100)+'%"></i></div><b>'+fmt(x.tokens)+'</b></div>').join("");
  }else $("models").innerHTML='<p class="muted">当前账号未返回模型级 Token 数据。</p>';

  const dg=d.diagnostics||{};
  $("diag").innerHTML=[["5H / Weekly",dg.quota],["Daily Token",dg.dailyCounts],["Token Breakdown",dg.tokenBreakdown],["Daily %",dg.dailyPercent]]
    .map(x=>'<div>'+x[0]+' · <b class="'+(x[1]?'ok':'')+'">'+(x[1]?'OK':'N/A')+'</b></div>').join("");

  if(!dg.dailyCounts)showMessage("额度数据已读取，但当前账号没有返回日级 Analytics；Token 项保持 --，不会填示例数据。");
  else showMessage("");
}

async function refresh(){
  $("refresh").disabled=true;
  showMessage("正在读取 ChatGPT Codex 真实数据…");
  try{
    const d=await chrome.runtime.sendMessage({type:"CODEXMETER_REFRESH"});
    if(d&&d.ok)render(d);else showMessage((d&&d.error)||"读取失败");
  }catch(e){showMessage(e.message||String(e))}
  finally{$("refresh").disabled=false}
}
$("refresh").onclick=refresh;
$("openFull").onclick=()=>chrome.tabs.create({url:chrome.runtime.getURL("dashboard.html?full=1")});
if(new URLSearchParams(location.search).get("full")==="1")document.body.classList.add("fullpage");
chrome.storage.local.get(["codexMeterData","codexMeterError"],x=>{if(x.codexMeterData&&x.codexMeterData.ok)render(x.codexMeterData);if(x.codexMeterError)showMessage(x.codexMeterError);refresh()});
setInterval(()=>{if(document.visibilityState==="visible")refresh()},5*60*1000);
