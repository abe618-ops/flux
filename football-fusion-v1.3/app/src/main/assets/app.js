'use strict';
const $=s=>document.querySelector(s);const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const LABEL={result:['主胜','客胜'],ou:['小2.5','大2.5'],parity:['双','单']};
const METHODS=[
 ['cegui','策轨数',.64,.57],['cegui_position','策轨元会运世实验',.64,.64],['zhouyi_ce','周易策数',.60,.60],['liuyao','六爻纳甲',.60,.54],['jinkou','金口诀总代表实验',.60,.64],['lingqi_classic','灵棋经计数',.60,.51],['lingqi','灵棋计数',.57,.57],['qimen','奇门拆补盘',.54,.57],['wuxing','五行数',.54,.57],['small_liuren','小六壬实验变体',.54,.51],['daliuren','大六壬四课三传',.51,.54],['meihua','三位数梅花',.51,.60],['shenyi','神易数实验代理',.48,.57],['taixuan','太玄81态',.48,.54],['meihua_canonical','梅花卦数',.48,.48]
].map(([id,name,r,o])=>({id,name,priorResult:r,priorOu:o}));
const RULES={
 daliuren:'四课三传实验数：三位数映十二支序，并以四课/三传思路加权后判奇偶。',
 jinkou:'金口诀五行数：三位数字按河图1–10数值加权。',
 shenyi:'神易数代理：64态域取数。',
 cegui:'策轨数：采用“大衍五十，其用四十九”的49域实验取数。',
 zhouyi_ce:'周易策数：49策余数反取。',
 cegui_position:'元会运世：构造五位位权数后合计。',
 qimen:'奇门拆补：九宫1–9循环取数。',
 meihua:'三位数梅花：上卦8、下卦8、动爻6合参。',
 liuyao:'六爻纳甲：六爻分别取6/7/8/9，合计判奇偶。',
 taixuan:'太玄数：按81态域取1–81。',
 wuxing:'五行数：河图1/6水、2/7火、3/8木、4/9金、5/10土的数值合计。',
 lingqi:'灵棋计数：十二位循环数叠加数字和。',
 small_liuren:'小六壬：六位循环取数。',
 lingqi_classic:'灵棋经计数：十二位加权计数。',
 meihua_canonical:'梅花卦数：卦数8与动爻6合参。'
};
const PRIOR_N=8;const TRIAD=['cegui','lingqi_classic','taixuan'];let tab='cast';let current=null;
let state=load();
function defaultState(){return {version:13,records:[],stats:{}}}
function load(){try{return {...defaultState(),...JSON.parse(localStorage.getItem('fusion-v13')||'{}')}}catch{return defaultState()}}
function save(){localStorage.setItem('fusion-v13',JSON.stringify(state))}
function stat(id){return state.stats[id]||(state.stats[id]={result:{n:0,h:0},ou:{n:0,h:0},parity:{n:0,h:0}})}
function score(m,dim){const st=stat(m.id)[dim],prior=dim==='result'?m.priorResult:dim==='ou'?m.priorOu:.5;return (prior*PRIOR_N+st.h)/(PRIOR_N+st.n)}
function composite(m){return .58*score(m,'result')+.42*score(m,'ou')}
function ordered(){return [...METHODS].sort((a,b)=>composite(b)-composite(a)||a.name.localeCompare(b.name,'zh-CN'))}
function triadMethods(methods){const by=Object.fromEntries(methods.map(x=>[x.id,x]));return TRIAD.map(id=>by[id]).filter(Boolean)}
function triadSignal(methods,dim){const rows=triadMethods(methods),ones=rows.filter(x=>x[dim]===1).length,zeros=rows.length-ones,dir=ones>zeros?1:0,unanimous=ones===rows.length||zeros===rows.length;return {dir,ones,zeros,unanimous,weight:unanimous?.18:.12,ids:[...TRIAD]}}
function blendTriad(base,methods,dim){const t=triadSignal(methods,dim);const rows=[base.winner,base.runner].map(x=>({...x,combined:(1-t.weight)*x.final+t.weight*(x.dir===t.dir?1:0)})).sort((a,b)=>b.combined-a.combined);const margin=rows[0].combined-rows[1].combined;return {winner:rows[0],runner:rows[1],margin,confidence:margin>=.16?'较强':margin>=.08?'中等':'谨慎',triad:t,base}}
function hash32(s){let h=2166136261>>>0;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)}h+=h<<13;h^=h>>>7;h+=h<<3;h^=h>>>17;h+=h<<5;return h>>>0}
function rng(seed){let x=hash32(seed)||0x12345678;return()=>{x^=x<<13;x^=x>>>17;x^=x<<5;return (x>>>0)/4294967296}}
function digits(n){return String(n).padStart(3,'0').slice(-3).split('').map(Number)}function hetu(d){return d===0?10:d}
function values(id,n,r){const [h,t,u]=digits(n),s=h+t+u;let p;
 switch(id){
  case'daliuren':p=1+((h*4+t*3+u*2+r)%12);break;
  case'jinkou':p=hetu(h)+2*hetu(t)+3*hetu(u)+r;break;
  case'shenyi':p=1+((n+s+r)%64);break;
  case'cegui':p=1+((n+s+r)%49);break;
  case'zhouyi_ce':p=50-(1+((n+2*s+r)%49));break;
  case'cegui_position':{const a=String((n*49+r*7919)%100000).padStart(5,'0').split('').map(Number);p=a.reduce((z,v,i)=>z+v*(i+1),0);break}
  case'qimen':p=1+((s+r)%9);break;
  case'meihua':p=(1+((h+t+r)%8))+(1+((t+u+r)%8))+(1+((s+r)%6));break;
  case'liuyao':{let x=n+r*17;p=0;for(let i=0;i<6;i++)p+=6+((x>>(i*2))&3);break}
  case'taixuan':p=1+((n+r)%81);break;
  case'wuxing':p=hetu(h)+hetu(t)+hetu(u)+r;break;
  case'lingqi':p=1+((n+r)%12)+s;break;
  case'small_liuren':p=1+((s+r)%6);break;
  case'lingqi_classic':p=1+((h*3+t*2+u+r)%12);break;
  case'meihua_canonical':p=2*(1+((h+t)%8))+(1+((t+u)%8))+(1+((s+r)%6));break;
  default:p=n+r;
 }
 const side=(p + h + 2*u + r)%2;const ou=(p + t + s + r*2)%2;const parity=Math.abs(p)%2;return {p,side,ou,parity};
}
function majority(arr,key){let a=0,b=0,total=0;arr.forEach(x=>{x[key]?b++:a++;total+=x.p});return a===b?(Math.abs(total)%2):b>a?1:0}
function castMethod(m,seed){const R=rng(seed+'|'+m.id),rounds=[];for(let i=0;i<9;i++){const n=Math.floor(R()*1000);rounds.push({code:n,...values(m.id,n,i)})}return {id:m.id,name:m.name,result:majority(rounds,'side'),ou:majority(rounds,'ou'),parity:majority(rounds,'parity'),value:rounds.reduce((z,x)=>z+x.p,0),codes:rounds.map(x=>String(x.code).padStart(3,'0')),rounds,rule:RULES[m.id]}}
function combos(a,k,start=0,p=[],out=[]){if(p.length===k){out.push([...p]);return out}for(let i=start;i<=a.length-(k-p.length);i++){p.push(a[i]);combos(a,k,i+1,p,out);p.pop()}return out}
function historicalCombo(ids,dim,dir){let n=0,h=0;for(const r of state.records){if(!r.actual)continue;const by=Object.fromEntries(r.methods.map(x=>[x.id,x]));if(!ids.every(id=>by[id]&&by[id][dim]===dir))continue;n++;if(r.actual[dim]===dir)h++}return {n,h,rate:n?h/n:0,sm:(h+2)/(n+4)}}
function resonance(methods,dim,dir){const voters=methods.filter(x=>x[dim]===dir).map(x=>x.id),rows=[];for(let k=2;k<=Math.min(5,voters.length);k++)for(const c of combos(voters,k)){const s=historicalCombo(c,dim,dir),min=k===2?4:k===3?3:2;rows.push({...s,ids:c,k,qualified:s.n>=min})}return rows.sort((a,b)=>(+b.qualified-+a.qualified)||b.sm-a.sm||b.n-a.n)}
function advice(methods,dim){const dirs=[0,1],all=methods.reduce((z,x)=>z+score(METHODS.find(m=>m.id===x.id),dim),0)||1;const rows=dirs.map(dir=>{const voters=methods.filter(x=>x[dim]===dir),base=voters.reduce((z,x)=>z+score(METHODS.find(m=>m.id===x.id),dim),0)/all,res=resonance(methods,dim,dir),q=res.filter(x=>x.qualified).slice(0,4),rq=q.length?q.reduce((z,x)=>z+x.sm*Math.log2(2+x.n),0)/q.reduce((z,x)=>z+Math.log2(2+x.n),0):.5;return {dir,base,res,final:.82*base+.18*rq}}).sort((a,b)=>b.final-a.final);const margin=rows[0].final-rows[1].final;return {winner:rows[0],runner:rows[1],margin,confidence:margin>=.16?'较强':margin>=.08?'中等':'谨慎'}}
function makeForecast(){const top=ordered().slice(0,2).map(x=>x.id);let attempts=0,methods,seed,aligned=false;do{attempts++;const a=new Uint32Array(4);crypto.getRandomValues(a);seed=[...a].join('-')+'-'+attempts;methods=METHODS.map(m=>castMethod(m,seed));const by=Object.fromEntries(methods.map(x=>[x.id,x]));aligned=by[top[0]].result===by[top[1]].result}while(!aligned&&attempts<500);const result=advice(methods,'result'),ouBase=advice(methods,'ou'),parityBase=advice(methods,'parity'),ou=blendTriad(ouBase,methods,'ou'),parity=blendTriad(parityBase,methods,'parity');return {id:'R'+String(state.records.length+1).padStart(4,'0'),createdAt:Date.now(),seed,attempts,top2:top,triad:[...TRIAD],methods,result:result.winner.dir,ou:ou.winner.dir,parity:parity.winner.dir,detail:{result,ou,parity},actual:null}}
function toast(t){const e=$('#toast');e.textContent=t;e.style.display='block';setTimeout(()=>e.style.display='none',2500)}
function render(){document.querySelectorAll('nav button').forEach(b=>b.classList.toggle('on',b.dataset.tab===tab));if(tab==='cast')renderCast();else if(tab==='review')renderReview();else renderRules()}
function renderCast(){const rank=ordered(),triadNames=TRIAD.map(id=>METHODS.find(m=>m.id===id)).filter(Boolean);if(!current){$('#app').innerHTML=`<section class="card"><h2>新一场</h2><input id="label" placeholder="场次标签，可留空"><button id="go" class="wide">开始自动合参</button><p class="tiny">按当前准确率排序；排名前两项若主客方向不同就自动重抽，直到一致。单双由每个术数独立取数，再进行总体加权合参。</p></section><section class="card"><h2>当前前两项</h2><div class="rank"><b>#1 ${esc(rank[0].name)}</b><span>${(composite(rank[0])*100).toFixed(1)}%</span></div><div class="rank"><b>#2 ${esc(rank[1].name)}</b><span>${(composite(rank[1])*100).toFixed(1)}%</span></div>${triadNames.map((m,i)=>`<div class="rank"><b>专项${i+1} ${esc(m.name)}</b><span>三项合参</span></div>`).join('')}</section>`;$('#go').onclick=()=>{current=makeForecast();current.label=$('#label').value.trim()||'盲测第'+(state.records.length+1)+'场';render()};return}
 const r=current,order=ordered().map(x=>x.id),sorted=[...r.methods].sort((a,b)=>order.indexOf(a.id)-order.indexOf(b.id)),by=Object.fromEntries(r.methods.map(x=>[x.id,x])),focus=[...r.top2.map(id=>by[id]),...TRIAD.map(id=>by[id])].filter(Boolean),counts={result:[0,0],ou:[0,0],parity:[0,0]};r.methods.forEach(x=>{counts.result[x.result]++;counts.ou[x.ou]++;counts.parity[x.parity]++});const triOu=r.detail.ou.triad,triParity=r.detail.parity.triad;
 $('#app').innerHTML=`<section class="hero"><div class="row"><b>${esc(r.label)}</b><button id="next" class="ghost">下一场</button></div><div class="pick">${LABEL.result[r.result]} / ${LABEL.ou[r.ou]} / ${LABEL.parity[r.parity]}</div><div class="meta"><span>主${counts.result[0]} 客${counts.result[1]}</span><span>小${counts.ou[0]} 大${counts.ou[1]}</span><span>双${counts.parity[0]} 单${counts.parity[1]}</span><span>前2项 ${r.attempts} 抽同向</span><span>三项 ${LABEL.ou[triOu.dir]} / ${LABEL.parity[triParity.dir]}</span></div></section><section class="card methods"><h2>重点五项</h2>${focus.map((x,i)=>{const isTop=i<2,isTri=i>=2,m=METHODS.find(z=>z.id===x.id);return `<div class="method ${isTop?'top':''}"><b>${isTop?'#'+(i+1):'专项'+(i-1)} ${esc(x.name)}${isTop?'<i class="badge">前2</i>':''}${isTri?'<i class="badge">三项</i>':''}</b><span class="picks">${LABEL.result[x.result]} / ${LABEL.ou[x.ou]} / ${LABEL.parity[x.parity]}</span><span class="score">${(composite(m)*100).toFixed(1)}%</span></div>`}).join('')}<p class="tiny">专项三项按多数决：2/3或3/3偏向同一方向；该信号只加入大小与单双合参，胜负规则保持不变。</p></section><section class="card methods"><h2>方法排序 · 高 → 低</h2>${sorted.map((x,i)=>{const m=METHODS.find(z=>z.id===x.id),top=r.top2.includes(x.id),tri=TRIAD.includes(x.id);return `<div class="method ${top?'top':''}"><b>#${i+1} ${esc(x.name)}${top?'<i class="badge">前2</i>':''}${tri?'<i class="badge">三项</i>':''}</b><span class="picks">${LABEL.result[x.result]} / ${LABEL.ou[x.ou]} / ${LABEL.parity[x.parity]}</span><span class="score">${(composite(m)*100).toFixed(1)}%</span></div>`}).join('')}</section><section class="card"><div class="kpis"><div class="kpi"><span>胜负</span><b>${LABEL.result[r.result]}</b></div><div class="kpi"><span>大小</span><b>${LABEL.ou[r.ou]}</b></div><div class="kpi"><span>单双</span><b>${LABEL.parity[r.parity]}</b></div></div><button id="settle" class="wide">输入赛果复盘</button><details><summary>共振与单双取数</summary><p class="tiny">胜负：原规则不变。大小/单双：原合参基础上再加入策轨数、灵棋经计数、太玄81态的三项多数信号；2/3同向为轻度加权，3/3同向为较强加权。</p>${sorted.map(x=>`<div class="rank"><span>${esc(x.name)} → <b>${LABEL.parity[x.parity]}</b></span><small>合数 ${x.value}<br>${esc(x.rule)}</small></div>`).join('')}</details></section>`;
 $('#next').onclick=()=>{current=null;render()};$('#settle').onclick=()=>openSettle(r)
}
function openSettle(r){const d=$('#dlg'),b=$('#dlgBody');b.innerHTML=`<h2>赛果复盘 · ${esc(r.label)}</h2><div class="row"><input id="h" type="number" min="0" max="30" placeholder="主队"><input id="a" type="number" min="0" max="30" placeholder="客队"></div><button id="saveResult" class="wide">保存并统计</button><p class="tiny">按90分钟赛果。保存后本场预测不再修改。</p>`;d.showModal();$('#saveResult').onclick=()=>{const h=Number($('#h').value),a=Number($('#a').value);if(!Number.isInteger(h)||!Number.isInteger(a)||h<0||a<0)return toast('请输入主客整数比分');const actual={result:h>a?0:h<a?1:null,ou:h+a>2.5?1:0,parity:(h+a)%2,h,a};r.actual=actual;state.records.push(r);for(const x of r.methods){const s=stat(x.id);if(actual.result!==null){s.result.n++;if(x.result===actual.result)s.result.h++}s.ou.n++;if(x.ou===actual.ou)s.ou.h++;s.parity.n++;if(x.parity===actual.parity)s.parity.h++}save();d.close();current=null;toast('已计入复盘');tab='review';render()}}
function renderReview(){const rank=ordered();let F={result:{n:0,h:0},ou:{n:0,h:0},parity:{n:0,h:0}};for(const r of state.records){if(!r.actual)continue;if(r.actual.result!==null){F.result.n++;if(r.result===r.actual.result)F.result.h++}F.ou.n++;if(r.ou===r.actual.ou)F.ou.h++;F.parity.n++;if(r.parity===r.actual.parity)F.parity.h++}const fmt=x=>x.n?`${x.h}/${x.n} · ${(100*x.h/x.n).toFixed(1)}%`:'等待样本';$('#app').innerHTML=`<section class="hero"><h2>总体合参成绩</h2><div class="kpis"><div class="kpi"><span>胜负</span><b>${fmt(F.result)}</b></div><div class="kpi"><span>大小</span><b>${fmt(F.ou)}</b></div><div class="kpi"><span>单双</span><b>${fmt(F.parity)}</b></div></div></section><section class="card"><h2>单法准确率排行</h2>${rank.map((m,i)=>{const s=stat(m.id);return `<div class="rank"><div><b>#${i+1} ${esc(m.name)}</b><br><small>综合 ${(100*composite(m)).toFixed(1)}%</small></div><div style="text-align:right"><span>胜 ${(100*score(m,'result')).toFixed(1)}% ${s.result.n?'·'+s.result.h+'/'+s.result.n:''}</span><br><span>大小 ${(100*score(m,'ou')).toFixed(1)}% ${s.ou.n?'·'+s.ou.h+'/'+s.ou.n:''}</span><br><span>单双 ${(100*score(m,'parity')).toFixed(1)}% ${s.parity.n?'·'+s.parity.h+'/'+s.parity.n:''}</span></div></div>`}).join('')}</section><section class="card"><h2>最近记录</h2>${[...state.records].reverse().slice(0,30).map(r=>`<div class="rank"><span>${esc(r.label)}<br><small>${new Date(r.createdAt).toLocaleString()}</small></span><span>${LABEL.result[r.result]} / ${LABEL.ou[r.ou]} / ${LABEL.parity[r.parity]}<br><small>${r.actual?r.actual.h+':'+r.actual.a:'未复盘'}</small></span></div>`).join('')||'<div class="empty">还没有复盘样本</div>'}</section>`}
function renderRules(){$('#app').innerHTML=`<section class="card"><h2>逐术数单双取数</h2><p class="tiny">以下是本软件用于盲测的“实验映射”，用于建立可复盘的独立单双样本，不宣称等同于某一古籍的唯一传统定法。</p>${ordered().map(m=>`<details><summary>${esc(m.name)}</summary><p class="tiny">${esc(RULES[m.id])}</p></details>`).join('')}</section><section class="card"><h2>合参规则</h2><p class="tiny">① 胜负/大小方法按历史准确率排序；② 排名前两项胜负不一致时自动重抽；③ 单法权重为主体，达标的2–5家共振有限修正；④ 单双由15家各自取数后独立加权；⑤ 小样本2/2、3/3不会直接当成稳定规律。</p></section>`}
document.querySelectorAll('nav button').forEach(b=>b.onclick=()=>{tab=b.dataset.tab;render()});$('#dlgClose').onclick=()=>$('#dlg').close();$('#reset').onclick=()=>{if(confirm('清空v1.3本地复盘记录？')){localStorage.removeItem('fusion-v13');state=defaultState();current=null;render();toast('已重置')}};render();
