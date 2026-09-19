#!/usr/bin/env node
'use strict';

const fs = require('fs');
const vm = require('vm');
const { webcrypto } = require('crypto');

function loadKernel() {
  const src = fs.readFileSync('fusion-lab-v13fix/kernel-audit/app.js','utf8');
  const marker = 'function toast(t){';
  const idx = src.indexOf(marker);
  if (idx < 0) throw new Error('Kernel split marker not found');
  const core = src.slice(0,idx) + '\n;globalThis.__K={METHODS,castMethod};';
  const sandbox = {
    crypto:webcrypto,
    localStorage:{getItem(){return null;},setItem(){}},
    console
  };
  vm.createContext(sandbox);
  vm.runInContext(core,sandbox,{filename:'app-core.js'});
  return sandbox.__K;
}

const STAGES = {
  result: {
    label:'胜负',
    methods:['lingqi','cegui','meihua']
  },
  ou: {
    label:'大小球',
    methods:['zhouyi_ce','lingqi_classic','small_liuren']
  },
  parity: {
    label:'单双',
    methods:['meihua_canonical','cegui_position','meihua']
  }
};

function freshSeed(tag,attempt) {
  const a = new Uint32Array(4);
  webcrypto.getRandomValues(a);
  return tag + '|' + attempt + '|' + [...a].join('-');
}

function castAll(K, seed) {
  const by = {};
  for (const m of K.METHODS) by[m.id] = K.castMethod(m,seed);
  return by;
}

function runConsensusStage(K, dim, maxAttempts=100000) {
  const cfg = STAGES[dim];
  for (let attempt=1; attempt<=maxAttempts; attempt++) {
    const seed = freshSeed(dim,attempt);
    const by = castAll(K,seed);
    const vals = cfg.methods.map(id => by[id][dim]);
    if (vals.every(v => v === vals[0])) {
      return {
        dimension:dim,
        label:cfg.label,
        value:vals[0],
        attempts:attempt,
        seed,
        methods:cfg.methods.map(id => ({
          id,
          name:K.METHODS.find(m=>m.id===id).name,
          value:by[id][dim],
          codes:by[id].codes,
          total_value:by[id].value
        }))
      };
    }
  }
  throw new Error('Consensus not reached for '+dim+' within '+maxAttempts+' attempts');
}

function runModel(K) {
  return {
    result:runConsensusStage(K,'result'),
    ou:runConsensusStage(K,'ou'),
    parity:runConsensusStage(K,'parity')
  };
}

function main() {
  const mode = process.argv[2] || 'diagnostic';
  const K = loadKernel();

  if (mode === 'diagnostic') {
    const n = Number(process.argv[3] || 10000);
    const outFile = process.argv[4] || 'three-stage-diagnostic.json';
    const stats = {
      n,
      result:{counts:[0,0],attempts:[]},
      ou:{counts:[0,0],attempts:[]},
      parity:{counts:[0,0],attempts:[]}
    };
    const sample = [];
    for (let i=0;i<n;i++) {
      const r = runModel(K);
      for (const dim of ['result','ou','parity']) {
        stats[dim].counts[r[dim].value]++;
        stats[dim].attempts.push(r[dim].attempts);
      }
      if (i<20) sample.push(r);
    }
    function summarise(x) {
      const s=[...x.attempts].sort((a,b)=>a-b);
      const q=p=>s[Math.min(s.length-1,Math.floor(p*(s.length-1)))];
      const avg=s.reduce((a,b)=>a+b,0)/s.length;
      return {
        counts:x.counts,
        share0:x.counts[0]/n,
        share1:x.counts[1]/n,
        mean_attempts:avg,
        median_attempts:q(.5),
        p90_attempts:q(.9),
        p99_attempts:q(.99),
        max_attempts:s[s.length-1]
      };
    }
    const payload = {
      model:'v1.4 experimental three-stage consensus',
      note:'No old weights/gates. Each dimension independently redraws until its three specified methods agree.',
      stages:STAGES,
      stats:{
        result:summarise(stats.result),
        ou:summarise(stats.ou),
        parity:summarise(stats.parity)
      },
      sample
    };
    fs.writeFileSync(outFile,JSON.stringify(payload,null,2));
    console.log(JSON.stringify(payload,null,2));
    return;
  }

  if (mode === 'predict') {
    const inFile = process.argv[3];
    const outFile = process.argv[4];
    if (!inFile || !outFile) throw new Error('Usage: three-stage-consensus.js predict <matches.json> <out.json>');
    const frozen=JSON.parse(fs.readFileSync(inFile,'utf8'));
    const out={
      model:'v1.4 experimental three-stage consensus',
      stages:STAGES,
      note:'One accepted consensus result per match. Match metadata is not passed into the kernel.',
      matches:[]
    };
    for (const m of frozen.matches) {
      out.matches.push({match:m,prediction:runModel(K)});
    }
    fs.writeFileSync(outFile,JSON.stringify(out,null,2));
    return;
  }

  throw new Error('Unknown mode: '+mode);
}

main();
