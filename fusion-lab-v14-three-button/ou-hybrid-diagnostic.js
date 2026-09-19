#!/usr/bin/env node
'use strict';

const fs=require('fs');
const vm=require('vm');
const {webcrypto}=require('crypto');

function loadCore(){
  const src=fs.readFileSync('fusion-lab-v14-three-button/source-overlay/app.original.js','utf8');
  const marker='function makeForecast(){';
  const idx=src.indexOf(marker);
  if(idx<0) throw new Error('core marker missing');
  const core=src.slice(0,idx)+'\n;globalThis.__K={METHODS,castMethod};';
  const sandbox={
    crypto:webcrypto,
    localStorage:{getItem(){return null},setItem(){}},
    console
  };
  vm.createContext(sandbox);
  vm.runInContext(core,sandbox,{filename:'app.original-core.js'});
  return sandbox.__K;
}

const K=loadCore();
const byId=Object.fromEntries(K.METHODS.map(m=>[m.id,m]));
const CORE=['zhouyi_ce','small_liuren','cegui_position'];

function freshSeed(tag,attempt){
  const a=new Uint32Array(4);webcrypto.getRandomValues(a);
  return tag+'|'+attempt+'|'+[...a].join('-');
}
function castAll(seed){
  const out={};
  for(const m of K.METHODS) out[m.id]=K.castMethod(m,seed);
  return out;
}
function weightedFirst(){
  const seed=freshSeed('weighted',1),all=castAll(seed);
  let big=0,small=0,total=0;
  for(const m of K.METHODS){
    const w=m.priorOu||.5; total+=w;
    if(all[m.id].ou===1)big+=w; else small+=w;
  }
  return{big,small,total,bigShare:big/total,dir:big>small?1:0};
}
function one(){
  const weighted=weightedFirst();
  for(let a=1;a<100000;a++){
    const all=castAll(freshSeed('pair',a));
    const x=all[CORE[0]].ou,y=all[CORE[1]].ou;
    if(x!==y) continue;
    const z=all[CORE[2]].ou;
    const agreement=z===x?3:2;
    const consensusBig=x===1?agreement/3:(3-agreement)/3;
    const finalBig=(consensusBig+weighted.bigShare)/2;
    return{attempts:a,agreement,consensus:x,weightedDir:weighted.dir,weightedBig:weighted.bigShare,final:finalBig>=.5?1:0,finalBig};
  }
  throw new Error('no consensus');
}

const n=Number(process.argv[2]||5000);
const stats={
  n,
  final:[0,0],
  weighted:[0,0],
  consensus:[0,0],
  agreement:{two:0,three:0},
  attempts:[],
  finalBig:[],
  weightedBig:[]
};
for(let i=0;i<n;i++){
  const r=one();
  stats.final[r.final]++;
  stats.weighted[r.weightedDir]++;
  stats.consensus[r.consensus]++;
  stats.agreement[r.agreement===3?'three':'two']++;
  stats.attempts.push(r.attempts);
  stats.finalBig.push(r.finalBig);
  stats.weightedBig.push(r.weightedBig);
}
const avg=a=>a.reduce((x,y)=>x+y,0)/a.length;
const sorted=[...stats.attempts].sort((a,b)=>a-b);
const q=p=>sorted[Math.floor((sorted.length-1)*p)];
const out={
  n,
  final:{small:stats.final[0],big:stats.final[1],bigShare:stats.final[1]/n},
  weightedFirst:{small:stats.weighted[0],big:stats.weighted[1],bigShare:stats.weighted[1]/n,meanBigVote:avg(stats.weightedBig)},
  pairConsensus:{small:stats.consensus[0],big:stats.consensus[1],bigShare:stats.consensus[1]/n,twoAgreement:stats.agreement.two,threeAgreement:stats.agreement.three},
  attempts:{mean:avg(stats.attempts),median:q(.5),p90:q(.9),p99:q(.99),max:sorted[sorted.length-1]},
  meanFinalBigScore:avg(stats.finalBig),
  coreMethods:CORE.map(id=>({id,name:byId[id].name,priorOu:byId[id].priorOu}))
};
console.log(JSON.stringify(out,null,2));
if(process.argv[3])fs.writeFileSync(process.argv[3],JSON.stringify(out,null,2));
