#!/usr/bin/env node
'use strict';

const fs = require('fs');
const vm = require('vm');
const { webcrypto } = require('crypto');

function loadKernel() {
  const src = fs.readFileSync('fusion-lab-v13fix/kernel-audit/app.js', 'utf8');
  const marker = 'function toast(t){';
  const idx = src.indexOf(marker);
  if (idx < 0) throw new Error('Kernel split marker not found');
  const core = src.slice(0, idx) + '\n;globalThis.__K={makeForecast,ordered,METHODS,LABEL,composite,score};';
  const sandbox = {
    crypto: webcrypto,
    localStorage: { getItem(){return null;}, setItem(){} },
    console
  };
  vm.createContext(sandbox);
  vm.runInContext(core, sandbox, { filename: 'app-core.js' });
  return sandbox.__K;
}

function summarizeCounts(c0, c1) {
  const n = c0 + c1;
  return {
    counts:[c0,c1],
    share0:n ? c0/n : 0,
    share1:n ? c1/n : 0,
    winner:c0===c1 ? null : (c1>c0 ? 1 : 0)
  };
}

function main() {
  const inFile = process.argv[2];
  const outFile = process.argv[3];
  const repeats = Number(process.argv[4] || 1000);
  if (!inFile || !outFile) throw new Error('Usage: method-benchmark.js <matches.json> <out.json> [repeats]');

  const frozen = JSON.parse(fs.readFileSync(inFile,'utf8'));
  const K = loadKernel();
  const methodOrder = K.ordered().map(x=>x.id);
  const names = Object.fromEntries(K.METHODS.map(x=>[x.id,x.name]));
  const out = {
    generated_at:new Date().toISOString(),
    repeats_per_match:repeats,
    kernel:{
      source:'fusion-lab-v13fix/kernel-audit/app.js',
      exact_core:true,
      note:'Runs accepted makeForecast() outputs from the exact extracted v1.3 core; no match data is passed into the kernel.',
      method_order:methodOrder,
      method_names:names
    },
    selection:frozen.selection,
    matches:[]
  };

  for (const m of frozen.matches) {
    const byMethod = {};
    for (const id of methodOrder) {
      byMethod[id] = {
        result:[0,0],
        ou:[0,0],
        parity:[0,0]
      };
    }
    let ensemble = {result:[0,0],ou:[0,0],parity:[0,0]};
    let attemptsSum = 0;
    for (let r=0;r<repeats;r++) {
      const f = K.makeForecast();
      attemptsSum += f.attempts;
      ensemble.result[f.result]++;
      ensemble.ou[f.ou]++;
      ensemble.parity[f.parity]++;
      for (const x of f.methods) {
        byMethod[x.id].result[x.result]++;
        byMethod[x.id].ou[x.ou]++;
        byMethod[x.id].parity[x.parity]++;
      }
    }
    const methods = {};
    for (const id of methodOrder) {
      methods[id] = {
        result:summarizeCounts(...byMethod[id].result),
        ou:summarizeCounts(...byMethod[id].ou),
        parity:summarizeCounts(...byMethod[id].parity)
      };
    }
    out.matches.push({
      match:m,
      repeats,
      attempts_avg:attemptsSum/repeats,
      ensemble:{
        result:summarizeCounts(...ensemble.result),
        ou:summarizeCounts(...ensemble.ou),
        parity:summarizeCounts(...ensemble.parity)
      },
      methods
    });
  }

  fs.writeFileSync(outFile, JSON.stringify(out,null,2));
}
main();
