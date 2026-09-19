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
    localStorage: {
      getItem() { return null; },
      setItem() {}
    },
    console
  };
  vm.createContext(sandbox);
  vm.runInContext(core, sandbox, { filename: 'app-core.js' });
  return sandbox.__K;
}

function tallyOne(K, n, keepRounds=false) {
  const counts = {
    result: [0,0],
    ou: [0,0],
    parity: [0,0]
  };
  let attemptsSum = 0;
  const rounds = [];
  for (let i=0;i<n;i++) {
    const f = K.makeForecast();
    counts.result[f.result]++;
    counts.ou[f.ou]++;
    counts.parity[f.parity]++;
    attemptsSum += f.attempts;
    if (keepRounds) {
      rounds.push({
        round: i + 1,
        seed: f.seed,
        attempts: f.attempts,
        result: f.result,
        ou: f.ou,
        parity: f.parity,
        top2: f.top2
      });
    }
  }
  function summary(key) {
    const c = counts[key];
    const winner = c[0] === c[1] ? null : (c[1] > c[0] ? 1 : 0);
    return {
      counts: c,
      share0: c[0] / n,
      share1: c[1] / n,
      winner
    };
  }
  return {
    n,
    result: summary('result'),
    ou: summary('ou'),
    parity: summary('parity'),
    attemptsAvg: attemptsSum / n,
    rounds
  };
}

function main() {
  const mode = process.argv[2];
  if (mode !== 'predict') throw new Error('Usage: backtest.js predict <matches.json> <out.json>');
  const inFile = process.argv[3];
  const outFile = process.argv[4];
  const matches = JSON.parse(fs.readFileSync(inFile, 'utf8'));
  const K = loadKernel();
  const depths = [10, 100, 1000];
  const out = {
    generated_at: new Date().toISOString(),
    kernel: {
      source: 'fusion-lab-v13fix/kernel-audit/app.js',
      exact_core: true,
      note: 'UI tail removed only; makeForecast/advice/method formulas are executed from the extracted v1.3 app.js source.',
      top2: K.ordered().slice(0,2).map(x => x.id),
      method_order: K.ordered().map(x => x.id)
    },
    matches: []
  };

  for (const m of matches.matches) {
    const item = { match: m, tests: {} };
    for (const d of depths) {
      item.tests[String(d)] = tallyOne(K, d, d === 10);
    }
    out.matches.push(item);
  }

  fs.writeFileSync(outFile, JSON.stringify(out, null, 2));
}

main();
