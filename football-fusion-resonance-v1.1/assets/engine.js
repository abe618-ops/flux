(function(root){
'use strict';
const VERSION='fusion-lab-1.1.0';
const FAMILIES={daliuren:'大六壬四课三传',qimen:'奇门拆补盘',meihua:'三位数梅花',liuyao:'六爻纳甲',taixuan:'太玄81态',wuxing:'五行数',lingqi:'灵棋计数',small_liuren:'小六壬实验变体',shenyi:'神易数实验代理',cegui:'策轨数',zhouyi_ce:'周易策数',lingqi_classic:'灵棋经计数',meihua_canonical:'梅花卦数',jinkou:'金口诀总代表实验',cegui_position:'策轨元会运世实验'};
const LABELS={result:['主胜','平局','客胜'],ou:['小2.5','大2.5'],parity:['双','单'],btts:['双方进球：否','双方进球：是'],htGoal:['上半场无球','上半场有球'],shGoal:['下半场无球','下半场有球'],ht:['主胜','平局','客胜'],sh:['主胜','平局','客胜'],htft:['胜/胜','胜/平','胜/负','平/胜','平/平','平/负','负/胜','负/平','负/负'],band:['0–1球','2–3球','4–6球','7+球'],goals:['0','1','2','3','4','5','6','7+'],handicap:['让球胜','让球平','让球负']};
const sum=a=>a.reduce((x,y)=>x+y,0),norm=a=>{const s=sum(a);return s?a.map(x=>x/s):a.map(()=>1/a.length);},argmax=a=>a.indexOf(Math.max(...a)),clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
// Chat-derived priors are deliberately weak. They preserve the current research ordering
// until enough locally settled matches exist, then live records dominate automatically.
const CHAT_PRIOR={
 result:{cegui:.64,cegui_position:.64,jinkou:.60,zhouyi_ce:.60,liuyao:.60,lingqi_classic:.60,lingqi:.57,qimen:.54,wuxing:.54,small_liuren:.54,daliuren:.51,meihua:.51,shenyi:.48,taixuan:.48,meihua_canonical:.48},
 ou:{jinkou:.64,cegui_position:.64,zhouyi_ce:.60,meihua:.60,shenyi:.57,cegui:.57,qimen:.57,wuxing:.57,lingqi:.57,daliuren:.54,liuyao:.54,taixuan:.54,small_liuren:.51,lingqi_classic:.51,meihua_canonical:.48}
};
const PRIOR_N=8;
const sidePick=p=>p[0]>=p[2]?0:2;
const actualSide=r=>r.result?(r.result.h>r.result.a?0:r.result.h<r.result.a?2:null):null;
function methodPerformance(records){
 const out={};for(const f of Object.keys(FAMILIES))out[f]={family:f,name:FAMILIES[f],result:{n:0,hits:0},ou:{n:0,hits:0}};
 const used=new Set();for(const r of records||[]){if(!r?.result||!r?.forecast?.methods||r.integrity===false||used.has(r.eventId))continue;used.add(r.eventId);const side=actualSide(r),ou=r.result.h+r.result.a>2.5?1:0;
  for(const m of r.forecast.methods){const s=out[m.family];if(!s)continue;if(side!==null){s.result.n++;s.result.hits+=sidePick(m.markets.result)===side?1:0;}s.ou.n++;s.ou.hits+=argmax(m.markets.ou)===ou?1:0;}
 }
 for(const [f,s] of Object.entries(out)){for(const dim of ['result','ou']){const x=s[dim],prior=CHAT_PRIOR[dim][f]??.5;x.live=x.n?x.hits/x.n:null;x.prior=prior;x.score=(prior*PRIOR_N+x.hits)/(PRIOR_N+x.n);x.interval=x.n?wilson(x.hits,x.n):null;}s.composite=.58*s.result.score+.42*s.ou.score;}
 return out;
}
function combinations(a,k,start=0,prefix=[],out=[]){if(prefix.length===k){out.push(prefix.slice());return out;}for(let i=start;i<=a.length-(k-prefix.length);i++){prefix.push(a[i]);combinations(a,k,i+1,prefix,out);prefix.pop();}return out;}
function comboStat(records,families,dim,dir){let n=0,hits=0;const used=new Set();for(const r of records||[]){if(!r?.result||!r?.forecast?.methods||r.integrity===false||used.has(r.eventId))continue;used.add(r.eventId);const actual=dim==='result'?actualSide(r):(r.result.h+r.result.a>2.5?1:0);if(actual===null)continue;const by=Object.fromEntries(r.forecast.methods.map(m=>[m.family,m]));if(!families.every(f=>by[f]))continue;const aligned=families.every(f=>(dim==='result'?sidePick(by[f].markets.result):argmax(by[f].markets.ou))===dir);if(!aligned)continue;n++;hits+=actual===dir?1:0;}const rate=n?hits/n:null,smoothed=(hits+2)/(n+4),interval=n?wilson(hits,n):null;return {n,hits,rate,smoothed,interval};}
function resonanceFor(records,forecast,dim,dir){const voters=forecast.methods.filter(m=>(dim==='result'?sidePick(m.markets.result):argmax(m.markets.ou))===dir).map(m=>m.family),rows=[];
 for(let size=2;size<=Math.min(5,voters.length);size++){for(const fams of combinations(voters,size)){const s=comboStat(records,fams,dim,dir),minN=size===2?4:size===3?3:2;rows.push({...s,size,families:fams,names:fams.map(f=>FAMILIES[f]),qualified:s.n>=minN});}}
 rows.sort((a,b)=>(Number(b.qualified)-Number(a.qualified))||((b.qualified?b.smoothed:0)-(a.qualified?a.smoothed:0))||(b.n-a.n)||(a.size-b.size));return rows;
}
function dimensionAdvice(records,forecast,dim){const perf=methodPerformance(records),dirs=dim==='result'?[0,2]:[0,1],totals={};
 for(const dir of dirs){const voters=forecast.methods.filter(m=>(dim==='result'?sidePick(m.markets.result):argmax(m.markets.ou))===dir),support=voters.reduce((z,m)=>z+perf[m.family][dim].score,0),all=forecast.methods.reduce((z,m)=>z+perf[m.family][dim].score,0)||1,base=support/all,res=resonanceFor(records,forecast,dim,dir),qualified=res.filter(x=>x.qualified).slice(0,4),rq=qualified.length?qualified.reduce((z,x)=>z+x.smoothed*Math.log2(2+x.n),0)/qualified.reduce((z,x)=>z+Math.log2(2+x.n),0):.5,final=.82*base+.18*rq;totals[dir]={dir,base,resonanceQuality:rq,final,voters:voters.map(m=>m.family),resonance:res};}
 const ranked=Object.values(totals).sort((a,b)=>b.final-a.final),winner=ranked[0],runner=ranked[1],margin=winner.final-runner.final;return {winner,runner,margin,confidence:margin>=.16?'较强':margin>=.08?'中等':'谨慎',totals};
}
function fusionAdvice(records,forecast){const perf=methodPerformance(records),result=dimensionAdvice(records,forecast,'result'),ou=dimensionAdvice(records,forecast,'ou'),methods=forecast.methods.map(m=>({family:m.family,name:m.name,resultPick:sidePick(m.markets.result),ouPick:argmax(m.markets.ou),...perf[m.family]})).sort((a,b)=>b.composite-a.composite);return {result,ou,methods,performance:perf,rule:'单法历史表现58%胜负+42%大小排序；当前方向按单法平滑历史权重汇总，共振仅占18%并设置最小样本门槛。单双不参与主建议。'};}
function resonanceLeaderboard(records,dim,maxSize=5){const families=Object.keys(FAMILIES),dirs=dim==='result'?[0,2]:[0,1],rows=[];for(let size=2;size<=maxSize;size++){for(const fams of combinations(families,size)){for(const dir of dirs){const s=comboStat(records,fams,dim,dir),minN=size===2?4:size===3?3:2;if(!s.n)continue;rows.push({...s,size,dir,families:fams,names:fams.map(f=>FAMILIES[f]),qualified:s.n>=minN,lower:s.interval?s.interval[0]:0});}}}rows.sort((a,b)=>(Number(b.qualified)-Number(a.qualified))||(b.lower-a.lower)||(b.smoothed-a.smoothed)||(b.n-a.n)||(a.size-b.size));return rows;}
function evaluateFusion(records){const out={result:{n:0,hits:0},ou:{n:0,hits:0}};const used=new Set();for(const r of records||[]){if(!r?.result||!r?.context?.fusionFreeze||r.integrity===false||used.has(r.eventId))continue;used.add(r.eventId);const fr=r.context.fusionFreeze,side=actualSide(r),ou=r.result.h+r.result.a>2.5?1:0;if(side!==null&&[0,2].includes(fr.result)){out.result.n++;out.result.hits+=fr.result===side?1:0;}if([0,1].includes(fr.ou)){out.ou.n++;out.ou.hits+=fr.ou===ou?1:0;}}for(const x of Object.values(out)){x.accuracy=x.n?x.hits/x.n:null;x.interval=x.n?wilson(x.hits,x.n):null;}return out;}
const validOdds=a=>Array.isArray(a)&&a.length===3&&a.every(x=>Number.isFinite(x)&&x>1&&x<1000);
function devig(o){if(!validOdds(o))throw Error('胜平负赔率须为三个大于1的数字');const inv=o.map(x=>1/x),t=sum(inv);return {p:inv.map(x=>x/t),r:1/t};}
function canonicalBook(s){const n=String(s).toLowerCase().replace(/[^a-z0-9\u4e00-\u9fff]/g,'');if(/12bet/.test(n))return '12BET';if(/10bet/.test(n))return '10BET';if(/pinn|平博|品博/.test(n))return 'Pinnacle';if(/betfair|必发/.test(n))return /exchange|交易所/.test(n)?'Betfair Exchange':'Betfair';if(/interwet/.test(n))return 'Interwetten';if(/bet365|^365$/.test(n))return 'Bet365';return String(s).trim();}
function enrichSnap(s){const d=devig(s.odds);const k=Array.isArray(s.k)&&s.k.length===3&&s.k.every(x=>Number.isFinite(x)&&x>0)?s.k:null;return {...s,p:d.p,r:d.r,k,j:k?k.map(x=>x/d.r):null,gap:k?k.map(x=>x-d.r):null};}
function dedupPaths(paths){const books=new Map();for(const path of paths){const book=canonicalBook(path.book);const rows=books.get(book)||[];for(const r of path.rows||[]){if(!validOdds(r.odds))continue;const s=enrichSnap({...r,book});const key=[r.kind,r.ts,JSON.stringify(r.odds),JSON.stringify(r.k)].join('|');if(!rows.some(x=>x._key===key))rows.push({...s,_key:key});}books.set(book,rows);}return [...books].map(([book,rows])=>({book,rows:rows.sort((a,b)=>(a.kind==='open'?-1:b.kind==='open'?1:0)||((a.ts||0)-(b.ts||0)))})).filter(p=>p.rows.length);}
function analyzeMarket(input){const paths=dedupPaths(input);if(!paths.length)throw Error('尚无可用的公司赔率');const p=[0,0,0];for(const x of paths)x.rows.at(-1).p.forEach((v,i)=>p[i]+=v/paths.length);
 const observations=paths.map(x=>{const a=x.rows[0],b=x.rows.at(-1),middle=x.rows.length>2?x.rows[Math.floor(x.rows.length/2)]:null;const movement=b.p.map((v,i)=>v-a.p[i]);const dk=a.j&&b.j?b.j.map((v,i)=>Math.log(a.j[i]/v)):null;
 const first=x.rows.length>2?x.rows[Math.max(1,Math.floor(x.rows.length*.65))]:a;
 const late=b.p.map((v,i)=>v-first.p[i]);
 const reversal=movement.some((v,i)=>Math.abs(v)>.015&&Math.abs(late[i])>.015&&v*late[i]<0);
 return {book:x.book,open:a,current:b,middle,movement,relativeMove:dk,reversal,drawDetached:b.gap!==null&&b.gap[1]>.035};});
 const twelve=observations.find(x=>x.book==='12BET'),ten=observations.find(x=>x.book==='10BET');let compare=null;
 if(twelve&&ten){const a=twelve.current,b=ten.current;const aligned=!!a.ts&&!!b.ts&&Math.abs(a.ts-b.ts)<=10*60*1000;compare={aligned,secondsApart:Math.abs((a.ts||0)-(b.ts||0))/1000,ratio:a.odds.map((v,i)=>v/b.odds[i]-1),pDifference:a.p.map((v,i)=>v-b.p[i]),source12:a,source10:b};}
 const rank=[0,1,2].sort((a,b)=>p[b]-p[a]);
 // Experimental gate, never a claim that a draw is impossible.
 const drawReduced=p[1]<.23&&paths.length>=2&&observations.every(x=>x.drawDetached)&&!observations.some(x=>x.reversal);
 return {paths,observations,p,rank,compare,drawReduced,drawNote:drawReduced?'平局相对偏低，仍保留其概率':'平局仍须保留',quality:paths.length<2?'单一来源':observations.some(x=>x.reversal)?'存在末段反向':'多来源可比',weightNote:'市场基线：去重后各公司等权；走势、凯利、10/12价差作实验特征，未标定为真实概率'};
}
const idx=(h,a)=>h>a?0:h===a?1:2;
function poisson(l,max=12){const p=[Math.exp(-l)];for(let i=1;i<=max;i++)p.push(p[i-1]*l/i);return norm(p);}
function binomial(n,f){const p=[];for(let k=0;k<=n;k++){let comb=1;for(let i=1;i<=k;i++)comb=comb*(n+1-i)/i;p.push(comb*f**k*(1-f)**(n-k));}return p;}
function markets(lh,la,half=.45,line=null){const ph=poisson(lh),pa=poisson(la);const m={};for(const k of Object.keys(LABELS))m[k]=LABELS[k].map(()=>0);const scores=[];const bins=ph.map((_,i)=>binomial(i,half));
 for(let h=0;h<ph.length;h++)for(let a=0;a<pa.length;a++){const p=ph[h]*pa[a],total=h+a,r=idx(h,a);scores.push({h,a,p});m.result[r]+=p;m.ou[total>2.5?1:0]+=p;m.parity[total%2]+=p;m.goals[Math.min(7,total)]+=p;m.band[total<=1?0:total<=3?1:total<=6?2:3]+=p;m.btts[h>0&&a>0?1:0]+=p;if(line!==null)m.handicap[idx(h+line,a)]+=p;
  for(let hh=0;hh<=h;hh++)for(let ha=0;ha<=a;ha++){const q=p*bins[h][hh]*bins[a][ha],hr=idx(hh,ha);m.ht[hr]+=q;m.sh[idx(h-hh,a-ha)]+=q;m.htft[hr*3+r]+=q;m.htGoal[hh+ha>0?1:0]+=q;m.shGoal[total-hh-ha>0?1:0]+=q;}
 }if(line===null)delete m.handicap;return {markets:m,scores:scores.sort((a,b)=>b.p-a.p),parameters:{home:lh,away:la,half,line}};}
function rng(seed){let a=0x811c9dc5,b=0x9e3779b9;for(const ch of seed){a=Math.imul(a^ch.charCodeAt(0),16777619)>>>0;b=Math.imul(b^a,2246822507)>>>0;}return ()=>{a=(a+0x6d2b79f5)>>>0;let t=a;t=Math.imul(t^(t>>>15),t|1);t^=t+Math.imul(t^(t>>>7),t|61);return ((t^(t>>>14)^b)>>>0)/4294967296;};}
const rel=(a,b)=>[0,-.3,.7,-.7,.3][('木火土金水'.indexOf(b)-'木火土金水'.indexOf(a)+5)%5];
function params(entry,family,stats){let z;const raw=entry.raw_features[family];if(raw){const s=stats[family];z=raw.map((v,i)=>clamp((v-s.mean[i])/(s.sd[i]||1),-2.5,2.5));}else if(family==='jinkou'){
 const n=entry.number.padStart(3,'0'),stems=['木','木','火','火','土','土','金','金','水','水'],branches=['水','土','木','木','土','火','火','土','金','金','土','水'];const host=stems[Number(n[0])],guest=branches[Number(n.slice(1))%12];z=[rel(host,guest)*2,0,0];
 }else if(family==='cegui_position'){
 const cg=entry.extended_charts.cegui;const cd=String(cg.ce).padStart(5,'0').slice(-5).split('').map(Number),gd=String(cg.gui).padStart(5,'0').slice(-5).split('').map(Number);const elements=['土','水','火','木','金','土','水','火','木','金'];z=[(rel(elements[gd[2]],elements[gd[4]])-rel(elements[gd[4]],elements[gd[2]]))*1.5,(gd[1]-4.5)/2.8,(gd[3]-gd[2])/5];
 }else throw Error('排盘特征缺失：'+family);
 const total=clamp(2.7*Math.exp(.28*z[1]),1.2,5.5),share=1/(1+Math.exp(-.65*z[0]));return [total*share,total*(1-share),z[2]<-.5?.35:z[2]>.5?.55:.45];}
function aggregate(items,weights){const keys=Object.keys(items[0].markets),m={},score=new Map();for(const k of keys)m[k]=items[0].markets[k].map(()=>0);const ws=norm(weights||items.map(()=>1));items.forEach((it,n)=>{for(const k of keys)it.markets[k].forEach((p,i)=>m[k][i]+=p*ws[n]);for(const s of it.scores){const k=s.h+':'+s.a;score.set(k,{h:s.h,a:s.a,p:(score.get(k)?.p||0)+s.p*ws[n]});}});return {markets:m,scores:[...score.values()].sort((a,b)=>b.p-a.p)};}
function castFamilies(catalog,seed,rounds,line,families){const all=[];for(const family of families){const rand=rng(seed+'|'+family),draws=[];for(let r=0;r<rounds;r++){const code=Math.floor(rand()*1000),entry=catalog.entries[code],ps=params(entry,family,catalog.feature_stats);const output=markets(...ps,line);draws.push({code:entry.number,...output});}const a=aggregate(draws);all.push({family,name:FAMILIES[family],codes:draws.map(x=>x.code),...a});}return all;}
function gateResult(direction){const p=[.15,.15,.15];p[direction]=.7;return p;}
function forecast(catalog,seed,options={}){
 const rounds=options.rounds||9,line=options.line??null;const families=Object.keys(FAMILIES),anchorFamilies=['daliuren','jinkou','qimen','liuyao'],supportFamilies=['shenyi','cegui','zhouyi_ce','cegui_position'];
 const maxAttempts=Math.max(1,Math.min(100,Number(options.maxAnchorAttempts||40)));let attempts=0,all,anchorDirections,aligned=false;
 do{attempts++;all=castFamilies(catalog,seed+'|anchor-attempt:'+attempts,rounds,line,families);const by=Object.fromEntries(all.map(x=>[x.family,x]));anchorDirections=anchorFamilies.map(f=>argmax(by[f].markets.result));aligned=anchorDirections[0]===anchorDirections[1]&&anchorDirections[2]===anchorDirections[3];}while(!aligned&&attempts<maxAttempts);
 const by=Object.fromEntries(all.map(x=>[x.family,x])),anchorDirection=anchorDirections[0],pairDirections={daliuren_jinkou:anchorDirections[0]===anchorDirections[1]?anchorDirections[0]:null,qimen_liuyao:anchorDirections[2]===anchorDirections[3]?anchorDirections[2]:null};const supportVotes=supportFamilies.map(f=>argmax(by[f].markets.result));const supportCounts=[0,0,0];supportVotes.forEach(x=>supportCounts[x]++);const supportMajority=supportVotes.length?argmax(supportCounts):null;const supportAgrees=supportMajority===anchorDirection&&supportCounts[anchorDirection]>=Math.ceil(supportVotes.length/2);const gateApplied=aligned&&supportAgrees;
 const weights=families.map(f=>Math.max(0,Number(options.weights?.[f]??1)));if(!sum(weights))throw Error('至少启用一种术数');
 const mixed=aggregate(all,weights);if(gateApplied)mixed.markets.result=gateResult(anchorDirection);const picks={};for(const [k,p] of Object.entries(mixed.markets))picks[k]=argmax(p);
 const primary=mixed.scores.find(s=>idx(s.h,s.a)===picks.result&&(s.h+s.a>2.5?1:0)===picks.ou&&(s.h+s.a)%2===picks.parity)||mixed.scores[0];
 const rand=rng(seed+'|INDEPENDENT_CONTROL');const control={result:Math.floor(rand()*3),ou:Math.floor(rand()*2),parity:Math.floor(rand()*2)};
 return {version:VERSION,seed,rounds,line,weights:Object.fromEntries(families.map((f,i)=>[f,weights[i]])),methods:all.map(a=>({family:a.family,name:a.name,codes:a.codes,markets:a.markets})),...mixed,picks,primary,control,anchorGate:{anchorFamilies,anchorDirections,anchorDirection,pairDirections,aligned,attempts,maxAttempts,supportFamilies,supportVotes,supportCounts,supportMajority,supportAgrees,applied:gateApplied,side:anchorDirection===0?'上盘':anchorDirection===2?'下盘':'平衡',rule:'大六壬与金口诀、奇门占卜盘与六爻纳甲分别求同向；不一致则重新随机起盘；两组一致后由神易数、策轨术、周易策数、策轨元会运世多数确认整体方向'} };
}
function scoreLabels(result,line=null){const {h,a,hh,ha}=result;if(!Number.isInteger(h)||!Number.isInteger(a)||h<0||a<0||h>30||a>30)throw Error('全场比分需为0–30的整数');const total=h+a;const y={result:idx(h,a),ou:total>2.5?1:0,parity:total%2,btts:h>0&&a>0?1:0,goals:Math.min(7,total),band:total<=1?0:total<=3?1:total<=6?2:3};if(line!==null)y.handicap=idx(h+line,a);
 if(hh!==null&&ha!==null&&hh!==undefined&&ha!==undefined){if(!Number.isInteger(hh)||!Number.isInteger(ha)||hh<0||ha<0||hh>h||ha>a)throw Error('半场比分必须非负且不超过全场比分');y.ht=idx(hh,ha);y.sh=idx(h-hh,a-ha);y.htft=y.ht*3+y.result;y.htGoal=hh+ha>0?1:0;y.shGoal=total-hh-ha>0?1:0;}return y;}
function wilson(h,n){if(!n)return null;const z=1.96,p=h/n,d=1+z*z/n,c=(p+z*z/(2*n))/d,m=z*Math.sqrt(p*(1-p)/n+z*z/(4*n*n))/d;return [c-m,c+m];}
function evaluate(records){const stats={};for(const k of Object.keys(LABELS))stats[k]={n:0,hits:0,inverseHits:0,brier:0,baselineCounts:LABELS[k].map(()=>0),controlN:0,controlHits:0};
 const used=new Set();for(const r of records){if(!r.result||!r.forecast||r.integrity===false||used.has(r.eventId))continue;used.add(r.eventId);const y=scoreLabels(r.result,r.forecast.line);for(const [k,v]of Object.entries(y)){const s=stats[k],p=r.forecast.markets[k];if(!p)continue;s.n++;s.baselineCounts[v]++;s.hits+=argmax(p)===v?1:0;s.inverseHits+=argmax(p.map(x=>(1-x)/(p.length-1)))===v?1:0;s.brier+=sum(p.map((x,i)=>(x-(i===v?1:0))**2));if(r.forecast.control[k]!==undefined){s.controlN++;s.controlHits+=r.forecast.control[k]===v?1:0;}}}
 for(const s of Object.values(stats)){s.accuracy=s.n?s.hits/s.n:null;s.interval=wilson(s.hits,s.n);s.brier=s.n?s.brier/s.n:null;s.baseline=s.n?Math.max(...s.baselineCounts)/s.n:null;}return stats;}
function proposeWeights(records){const settled=records.filter(x=>x.result&&x.forecast&&x.integrity!==false);if(settled.length<30)return {ready:false,n:settled.length,note:'至少30个有完整赛果的独立样本后生成候选；10/20场只展示阶段复盘'};const cut=Math.floor(settled.length*2/3),train=settled.slice(0,cut),test=settled.slice(cut),weights={};
 for(const f of Object.keys(FAMILIES)){let loss=0,n=0;for(const r of train){const p=r.forecast.methods.find(x=>x.family===f)?.markets.result;if(!p)continue;const y=scoreLabels(r.result).result;loss+=sum(p.map((x,i)=>(x-(i===y?1:0))**2));n++;}weights[f]=n?Math.exp(-3*loss/n):1;}
 let oldLoss=0,newLoss=0;for(const r of test){const y=scoreLabels(r.result).result,p=norm([0,1,2].map(i=>sum(r.forecast.methods.map(x=>x.markets.result[i]*weights[x.family]))));oldLoss+=sum(r.forecast.markets.result.map((x,i)=>(x-(i===y?1:0))**2));newLoss+=sum(p.map((x,i)=>(x-(i===y?1:0))**2));}
 return {ready:true,train:train.length,validation:test.length,weights,oldBrier:oldLoss/test.length,newBrier:newLoss/test.length,better:newLoss<oldLoss,note:'这是按时间分组的候选诊断；只可用于未来新批次，既有预测保持原样。反向命中只作并行对照。'};}
root.FusionEngine={VERSION,FAMILIES,LABELS,CHAT_PRIOR,devig,canonicalBook,enrichSnap,dedupPaths,analyzeMarket,forecast,markets,scoreLabels,evaluate,proposeWeights,methodPerformance,resonanceFor,resonanceLeaderboard,fusionAdvice,evaluateFusion,sidePick,wilson,argmax,norm,idx};
if(typeof module!=='undefined')module.exports=root.FusionEngine;
})(typeof window!=='undefined'?window:globalThis);
