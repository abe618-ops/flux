'use strict';
const $=s=>document.querySelector(s);
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const METHODS=[
 ['cegui','策轨数',.57],
 ['cegui_position','策轨元会运世实验',.64],
 ['zhouyi_ce','周易策数',.60],
 ['liuyao','六爻纳甲',.54],
 ['jinkou','金口诀总代表实验',.64],
 ['lingqi_classic','灵棋经计数',.51],
 ['lingqi','灵棋计数',.57],
 ['qimen','奇门拆补盘',.57],
 ['wuxing','五行数',.57],
 ['small_liuren','小六壬实验变体',.51],
 ['daliuren','大六壬四课三传',.54],
 ['meihua','三位数梅花',.60],
 ['shenyi','神易数实验代理',.57],
 ['taixuan','太玄81态',.54],
 ['meihua_canonical','梅花卦数',.48]
].map(([id,name,ouWeight])=>({id,name,ouWeight}));
const STAGES={
 result:{label:'胜负',methods:['lingqi','cegui','meihua'],text:v=>v===0?'主胜':'客胜'},
 ou:{label:'大小',methods:['zhouyi_ce','small_liuren','cegui_position'],text:v=>v===0?'小2.5':'大2.5'},
 parity:{label:'单双',methods:['meihua_canonical','cegui_position','meihua'],text:v=>v===0?'双':'单'}
};
function hash32(s){let h=2166136261>>>0;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)}h+=h<<13;h^=h>>>7;h+=h<<3;h^=h>>>17;h+=h<<5;return h>>>0}
function rng(seed){let x=hash32(seed)||0x12345678;return()=>{x^=x<<13;x^=x>>>17;x^=x<<5;return(x>>>0)/4294967296}}
function digits(n){return String(n).padStart(3,'0').slice(-3).split('').map(Number)}function hetu(d){return d===0?10:d}
function values(id,n,r){const[h,t,u]=digits(n),s=h+t+u;let p;switch(id){case'daliuren':p=1+((h*4+t*3+u*2+r)%12);break;case'jinkou':p=hetu(h)+2*hetu(t)+3*hetu(u)+r;break;case'shenyi':p=1+((n+s+r)%64);break;case'cegui':p=1+((n+s+r)%49);break;case'zhouyi_ce':p=50-(1+((n+2*s+r)%49));break;case'cegui_position':{const a=String((n*49+r*7919)%100000).padStart(5,'0').split('').map(Number);p=a.reduce((z,v,i)=>z+v*(i+1),0);break}case'qimen':p=1+((s+r)%9);break;case'meihua':p=(1+((h+t+r)%8))+(1+((t+u+r)%8))+(1+((s+r)%6));break;case'liuyao':{let x=n+r*17;p=0;for(let i=0;i<6;i++)p+=6+((x>>(i*2))&3);break}case'taixuan':p=1+((n+r)%81);break;case'wuxing':p=hetu(h)+hetu(t)+hetu(u)+r;break;case'lingqi':p=1+((n+r)%12)+s;break;case'small_liuren':p=1+((s+r)%6);break;case'lingqi_classic':p=1+((h*3+t*2+u+r)%12);break;case'meihua_canonical':p=2*(1+((h+t)%8))+(1+((t+u)%8))+(1+((s+r)%6));break;default:p=n+r}const side=(p+h+2*u+r)%2,ou=(p+t+s+r*2)%2,parity=Math.abs(p)%2;return{p,side,ou,parity}}
function majority(arr,key){let a=0,b=0,total=0;arr.forEach(x=>{x[key]?b++:a++;total+=x.p});return a===b?(Math.abs(total)%2):b>a?1:0}
function castMethod(m,seed){const R=rng(seed+'|'+m.id),rounds=[];for(let i=0;i<9;i++){const n=Math.floor(R()*1000);rounds.push({code:n,...values(m.id,n,i)})}return{id:m.id,name:m.name,result:majority(rounds,'side'),ou:majority(rounds,'ou'),parity:majority(rounds,'parity'),value:rounds.reduce((z,x)=>z+x.p,0),codes:rounds.map(x=>String(x.code).padStart(3,'0'))}}
function freshSeed(tag,attempt){const a=new Uint32Array(4);crypto.getRandomValues(a);return tag+'|'+attempt+'|'+[...a].join('-')}
function castAll(seed){const by={};for(const m of METHODS)by[m.id]=castMethod(m,seed);return by}
function weightedOuFirstDraw(){
 const seed=freshSeed('ou-weighted-first',1),by=castAll(seed);
 let small=0,big=0,total=0;
 const rows=METHODS.map(m=>{
   const w=m.ouWeight||.5,dir=by[m.id].ou;
   total+=w;if(dir===1)big+=w;else small+=w;
   return{id:m.id,name:m.name,value:dir,text:STAGES.ou.text(dir),weight:w};
 });
 return{seed,small,big,total,smallShare:small/total,bigShare:big/total,value:big>small?1:0,text:STAGES.ou.text(big>small?1:0),methods:rows};
}
function runOuPair(maxAttempts=100000){
 const ids=['zhouyi_ce','small_liuren'];
 for(let attempt=1;attempt<=maxAttempts;attempt++){
   const seed=freshSeed('ou-top2',attempt),by=castAll(seed);
   const a=by[ids[0]].ou,b=by[ids[1]].ou;
   if(a!==b)continue;
   return{
     mode:'top2',label:'最高两者一致',value:a,text:STAGES.ou.text(a),attempts:attempt,seed,
     methods:ids.map(id=>({id,name:by[id].name,value:by[id].ou,text:STAGES.ou.text(by[id].ou),codes:by[id].codes,total:by[id].value}))
   };
 }
 throw new Error('大小项前两项未在限定次数内形成一致');
}
function runOuTriple(maxAttempts=100000){
 const ids=['zhouyi_ce','small_liuren','cegui_position'];
 for(let attempt=1;attempt<=maxAttempts;attempt++){
   const seed=freshSeed('ou-top3',attempt),by=castAll(seed);
   const vals=ids.map(id=>by[id].ou);
   if(!vals.every(v=>v===vals[0]))continue;
   return{
     mode:'top3',label:'最高三个一致',value:vals[0],text:STAGES.ou.text(vals[0]),attempts:attempt,seed,
     methods:ids.map(id=>({id,name:by[id].name,value:by[id].ou,text:STAGES.ou.text(by[id].ou),codes:by[id].codes,total:by[id].value}))
   };
 }
 throw new Error('大小项前三项未在限定次数内形成一致');
}
function runOuThreeModels(){
 const top2=runOuPair();
 const top3=runOuTriple();
 const weighted=weightedOuFirstDraw();
 const votes=[top2.value,top3.value,weighted.value];
 const bigVotes=votes.filter(v=>v===1).length;
 const value=bigVotes>=2?1:0;
 return{
   dim:'ou',label:'大小',value,text:STAGES.ou.text(value),
   attempts:top2.attempts+top3.attempts,
   top2,top3,weighted,
   methods:top3.methods,
   voteBig:bigVotes,
   voteSmall:3-bigVotes
 };
}
function runStage(dim,maxAttempts=100000){const cfg=STAGES[dim];for(let attempt=1;attempt<=maxAttempts;attempt++){const seed=freshSeed(dim,attempt),by=castAll(seed),vals=cfg.methods.map(id=>by[id][dim]);if(vals.every(v=>v===vals[0]))return{dim,label:cfg.label,value:vals[0],text:cfg.text(vals[0]),attempts:attempt,seed,methods:cfg.methods.map(id=>({id,name:by[id].name,value:by[id][dim],text:cfg.text(by[id][dim]),codes:by[id].codes,total:by[id].value}))}}throw new Error('本项未在限定次数内形成三者一致')}
let tab='cast',active='result',state=load(),current=state.current||null;
function defaultState(){return{version:14,current:null,history:[]}}function load(){try{return{...defaultState(),...JSON.parse(localStorage.getItem('fusion-v14-three-button')||'{}')}}catch{return defaultState()}}function save(){state.current=current;localStorage.setItem('fusion-v14-three-button',JSON.stringify(state))}
function toast(t){const e=$('#toast');e.textContent=t;e.style.display='block';setTimeout(()=>e.style.display='none',2200)}
function makeRound(){return{id:'T'+String((state.history?.length||0)+1).padStart(4,'0'),createdAt:Date.now(),result:runStage('result'),ou:runOuThreeModels(),parity:runStage('parity')}}function ensureRound(){if(current)return;current=makeRound();save()}
function rerun(dim){ensureRound();current[dim]=dim==='ou'?runOuThreeModels():runStage(dim);save();renderCast();toast(dim==='ou'?'大小已按“两高一致 + 三高一致 + 默认加权”重新计算':STAGES[dim].label+'已重新抽到三者一致')}
function newRound(){if(current){state.history=state.history||[];state.history.unshift(current);if(state.history.length>100)state.history=state.history.slice(0,100)}current=makeRound();active='result';save();render();toast('新一轮已完成三项一致抽取')}
function render(){document.querySelectorAll('nav button').forEach(b=>b.classList.toggle('on',b.dataset.tab===tab));if(tab==='cast')renderCast();else if(tab==='history')renderHistory();else renderRules()}
function renderCast(){
 ensureRound();
 const s=current[active],cfg=STAGES[active];
 const buttons=['result','ou','parity'].map(dim=>{const x=current[dim];return'<button class="stage-tab '+(active===dim?'on':'')+'" data-stage="'+dim+'">'+STAGES[dim].label+'<small>'+esc(x.text)+'</small></button>'}).join('');
 const summary=['result','ou','parity'].map(dim=>'<button class="'+(active===dim?'on':'')+'" data-sum="'+dim+'"><span class="label">'+STAGES[dim].label+'</span><span class="val">'+esc(current[dim].text)+'</span></button>').join('');
 let detail='',heroMethods=s.methods||[];
 if(active==='ou'){
   const w=s.weighted;
   detail='<section class="card"><h2>大小三法共同测试</h2>'+
     '<div class="ou-grid">'+
       '<div class="ou-box"><span>① 最高两者一致</span><b>'+esc(s.top2.text)+'</b><small>自动抽 '+s.top2.attempts+' 次 · 周易策数 + 小六壬</small></div>'+
       '<div class="ou-box"><span>② 最高三个一致</span><b>'+esc(s.top3.text)+'</b><small>自动抽 '+s.top3.attempts+' 次 · 再加策轨元会运世</small></div>'+
       '<div class="ou-box"><span>③ 默认加权重</span><b>'+esc(w.text)+'</b><small>第一次抽取15法 · 小 '+(w.smallShare*100).toFixed(1)+'% / 大 '+(w.bigShare*100).toFixed(1)+'%</small></div>'+
     '</div>'+
     '<div class="ou-final"><span>三法多数综合</span><b>'+esc(s.text)+'</b><small>小 '+s.voteSmall+' 票 / 大 '+s.voteBig+' 票</small></div>'+
     '<div class="tiny ou-note">三种测试彼此独立：两高一致会一直抽到前两项一致；三高一致会一直抽到前三项全部一致；默认加权只取第一次抽取的全部15种方法，按原大小权重计算。最终取三种结果的多数方向。</div>'+
   '</section>';
   heroMethods=[];
 }
 $('#app').innerHTML='<section class="hero"><div class="stage-tabs">'+buttons+'</div><div class="result-box"><div class="eyebrow">'+esc(cfg.label)+' · '+(active==='ou'?'两高一致 / 三高一致 / 默认加权':'三者共同指向')+'</div><div class="result">'+esc(s.text)+'</div><div class="attempts">'+(active==='ou'?('三法多数：小 '+s.voteSmall+' / 大 '+s.voteBig):('自动抽取 '+s.attempts+' 次后达成三者一致'))+'</div>'+(heroMethods.length?'<div class="consensus">'+heroMethods.map(m=>'<span>'+esc(m.name)+' → <b>'+esc(m.text)+'</b></span>').join('')+'</div>':'')+'</div></section>'+
 (active==='ou'?'':'<section class="card methods"><h2>本次共同指向</h2>'+s.methods.map(m=>'<div class="method-row"><b>'+esc(m.name)+'</b><span class="dir">'+esc(m.text)+'</span></div>').join('')+'</section>')+
 detail+
 '<section class="card"><button id="rerun" class="wide">重新抽取“'+esc(cfg.label)+'”</button><h2 style="margin-top:14px">本轮三项结果</h2><div class="summary">'+summary+'</div><button id="newRound" class="wide ghost">开始新一轮</button></section>';
 document.querySelectorAll('[data-stage]').forEach(b=>b.onclick=()=>{active=b.dataset.stage;renderCast()});
 document.querySelectorAll('[data-sum]').forEach(b=>b.onclick=()=>{active=b.dataset.sum;renderCast()});
 $('#rerun').onclick=()=>rerun(active);$('#newRound').onclick=newRound;
}
function renderHistory(){const all=[...(current?[current]:[]),...(state.history||[])];$('#app').innerHTML='<section class="card"><h2>最近记录</h2>'+(all.length?all.slice(0,50).map((r,i)=>'<div class="history-item"><div class="history-head"><span>'+(i===0&&current===r?'当前轮':'历史轮')+' '+esc(r.id)+'</span><span>'+new Date(r.createdAt).toLocaleString()+'</span></div><div class="history-picks">'+esc(r.result.text)+' / '+esc(r.ou.text)+' / '+esc(r.parity.text)+'</div><div class="tiny">抽取次数：胜负 '+r.result.attempts+' · 大小 '+r.ou.attempts+' · 单双 '+r.parity.attempts+'</div></div>').join(''):'<div class="empty">暂无记录</div>')+'</section>'}
function renderRules(){$('#app').innerHTML='<section class="card"><h2>三轮测试法</h2><div class="rule-line"><b>① 胜负</b><div class="tiny">灵棋计数 + 策轨数 + 三位数梅花。三者方向不同就继续自动抽，直到三者同时指向主胜或同时指向客胜。</div></div><div class="rule-line"><b>② 大小</b><div class="tiny">同时做三种独立测试：①最高两者一致——周易策数 + 小六壬不同就继续抽，直到一致；②最高三个一致——周易策数 + 小六壬 + 策轨元会运世不同就继续抽，直到三者完全一致；③默认加权——只使用第一次抽取的全部15种方法，按原大小权重合计。最终用三种测试的多数方向给出大小结果。</div></div><div class="rule-line"><b>③ 单双</b><div class="tiny">梅花卦数 + 策轨元会运世实验 + 三位数梅花。三者一致才显示双或单。</div></div><p class="tiny">三个项目彼此独立抽取，不使用V1.3旧权重，也不再使用“奇门+六爻前2同向”作为门控。</p></section>'}
document.querySelectorAll('nav button').forEach(b=>b.onclick=()=>{tab=b.dataset.tab;render()});$('#reset').onclick=()=>{if(confirm('清空当前轮和历史记录？')){localStorage.removeItem('fusion-v14-three-button');state=defaultState();current=null;active='result';render();toast('已重置')}};render();