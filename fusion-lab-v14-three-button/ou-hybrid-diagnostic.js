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
  const sandbox={crypto:webcrypto,localStorage:{getItem(){return null},setItem(){}},console};
  vm.createContext(sandbox);
  vm.runInContext(core,sandbox,{filename:'app.original-core.js'});
  return sandbox.__K;
}

const K=loadCore();
const byId=Object.fromEntries(K.METHODS.map(m=>[m.id,m]));
const TOP2=['zhouyi_ce','small_liuren'];
const TOP3=['zhouyi_ce','small_liuren','cegui_position'];

function freshSeed(tag,attempt){
  const a=new Uint32Array(4);webcrypto.getRandomValues(a);
  return tag+'|'+attempt+'|'+[...a].join('-');
}
function castAll(seed){
  const out={};
  for(const m of K.METHODS)out[m.id]=K.castMethod(m,seed);
  return out;
}
function weightedFirst(){
  const all=castAll(freshSeed('weighted',1));
  let big=0,small=0,total=0;
  for(const m of K.METHODS){
    const w=m.priorOu||.5;total+=w;
    if(all[m.id].ou===1)big+=w;else small+=w;
  }
  return{dir:big>small?1:0,bigShare:big/total};
}
function consensus(ids,tag){
  for(let a=1;a<100000;a++){
    const all=castAll(freshSeed(tag,a));
    const vals=ids.map(id=>all[id].ou);
    if(vals.every(v=>v===vals[0]))return{dir:vals[0],attempts:a};
  }
  throw new Error('no consensus');
}
function one(){
  const top2=consensus(TOP2,'top2');
  const top3=consensus(TOP3,'top3');
  const weighted=weightedFirst();
  const dirs=[top2.dir,top3.dir,weighted.dir];
  const bigVotes=dirs.filter(v=>v===1).length;
  return{top2,top3,weighted,final:bigVotes>=2?1:0,bigVotes};
}

const n=Number(process.argv[2]||5000);
const stats={final:[0,0],top2:[0,0],top3:[0,0],weighted:[0,0],a2:[],a3:[],bigVotes:[0,0,0,0]};
for(let i=0;i<n;i++){
  const r=one();
  stats.final[r.final]++;stats.top2[r.top2.dir]++;stats.top3[r.top3.dir]++;stats.weighted[r.weighted.dir]++;
  stats.a2.push(r.top2.attempts);stats.a3.push(r.top3.attempts);stats.bigVotes[r.bigVotes]++;
}
const avg=a=>a.reduce((x,y)=>x+y,0)/a.length;
const pct=x=>x/n;
const out={
  n,
  final:{small:stats.final[0],big:stats.final[1],bigShare:pct(stats.final[1])},
  top2:{small:stats.top2[0],big:stats.top2[1],bigShare:pct(stats.top2[1]),meanAttempts:avg(stats.a2),methods:TOP2.map(id=>byId[id].name)},
  top3:{small:stats.top3[0],big:stats.top3[1],bigShare:pct(stats.top3[1]),meanAttempts:avg(stats.a3),methods:TOP3.map(id=>byId[id].name)},
  weighted:{small:stats.weighted[0],big:stats.weighted[1],bigShare:pct(stats.weighted[1])},
  bigVoteHistogram:stats.bigVotes
};
console.log(JSON.stringify(out,null,2));
if(process.argv[3])fs.writeFileSync(process.argv[3],JSON.stringify(out,null,2));
