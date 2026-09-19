#!/usr/bin/env python3
import json, statistics
from pathlib import Path

base=Path('fusion-lab-v13fix/kernel-audit/method-rank')
pred=json.load(open(base/'frozen_method_predictions.json',encoding='utf-8'))
revealed=json.load(open(base/'method_ranking_revealed.json',encoding='utf-8'))

names=pred['kernel']['method_names']
order=pred['kernel']['method_order']
repeats=pred['repeats_per_match']
matches=revealed['revealed']

def safe(h,n):
    return h/n if n else None

def pct(v):
    return '—' if v is None else f'{v:.1%}'

def score_batch(items, mid):
    s={
        'result_hits':0,'result_n':0,
        'ou_hits':0,'ou_n':0,
        'parity_hits':0,'parity_n':0,
        'match_result_hits':0,'match_result_n':0,
        'match_ou_hits':0,'match_ou_n':0,
        'match_parity_hits':0,'match_parity_n':0,
    }
    for x in items:
        a=x['actual']; m=x['methods'][mid]
        if a['result'] is not None:
            s['result_hits'] += m['result']['counts'][a['result']]
            s['result_n'] += repeats
            if m['result']['winner'] is not None:
                s['match_result_n'] += 1
                s['match_result_hits'] += int(m['result']['winner']==a['result'])
        s['ou_hits'] += m['ou']['counts'][a['ou']]
        s['ou_n'] += repeats
        if m['ou']['winner'] is not None:
            s['match_ou_n'] += 1
            s['match_ou_hits'] += int(m['ou']['winner']==a['ou'])
        s['parity_hits'] += m['parity']['counts'][a['parity']]
        s['parity_n'] += repeats
        if m['parity']['winner'] is not None:
            s['match_parity_n'] += 1
            s['match_parity_hits'] += int(m['parity']['winner']==a['parity'])
    rp=safe(s['result_hits'],s['result_n'])
    op=safe(s['ou_hits'],s['ou_n'])
    pp=safe(s['parity_hits'],s['parity_n'])
    comp=sum(v for v in (rp,op,pp) if v is not None)/len([v for v in (rp,op,pp) if v is not None])
    mr=safe(s['match_result_hits'],s['match_result_n'])
    mo=safe(s['match_ou_hits'],s['match_ou_n'])
    mp=safe(s['match_parity_hits'],s['match_parity_n'])
    mvals=[v for v in (mr,mo,mp) if v is not None]
    mcomp=sum(mvals)/len(mvals) if mvals else None
    return {
        'run_level':{'result':rp,'ou':op,'parity':pp,'composite':comp},
        'match_level':{
            'result':mr,'ou':mo,'parity':mp,'composite':mcomp,
            'result_record':[s['match_result_hits'],s['match_result_n']],
            'ou_record':[s['match_ou_hits'],s['match_ou_n']],
            'parity_record':[s['match_parity_hits'],s['match_parity_n']]
        }
    }

batches=[]
for b in range(1,11):
    items=[x for x in matches if x['match']['batch']==b]
    batch={'batch':b,'matches':[],'methods':{}}
    for x in items:
        batch['matches'].append({
            'slot':x['match']['slot'],
            'date':x['match']['date'],
            'home_team':x['match']['home_team'],
            'away_team':x['match']['away_team'],
            'score':f"{x['actual']['h']}:{x['actual']['a']}"
        })
    for mid in order:
        batch['methods'][mid]=score_batch(items,mid)
    batches.append(batch)

# Cumulative after each 10-match batch.
cumulative=[]
for b in range(1,11):
    items=[x for x in matches if x['match']['batch']<=b]
    row={'through_batch':b,'matches':len(items),'methods':{}}
    for mid in order:
        row['methods'][mid]=score_batch(items,mid)
    cumulative.append(row)

out={
    'note':'Raw all-method comparison only. No method filtering, no reweighting, no use of prior ranking rules.',
    'source_frozen_predictions':'frozen_method_predictions.json',
    'source_reveal':'method_ranking_revealed.json',
    'repeats_per_match':repeats,
    'batches':batches,
    'cumulative':cumulative
}
json.dump(out,open(base/'ten-match-raw-comparison.json','w',encoding='utf-8'),ensure_ascii=False,indent=2)

lines=[]
lines.append('# V1.3 全方法原始核验：每10场一组')
lines.append('')
lines.append('本报告不筛选术数、不设新权重、不采用此前排序规则。直接使用已经冻结的100场与冻结预测，按比赛顺序每10场为一组开盒核验。')
lines.append('')
for batch in batches:
    lines.append(f"## 第{batch['batch']}组（10场）")
    lines.append('')
    lines.append('| # | 日期 | 主队 | 客队 | 实际比分 |')
    lines.append('|---:|---|---|---|---:|')
    for m in batch['matches']:
        lines.append(f"| {m['slot']} | {m['date']} | {m['home_team']} | {m['away_team']} | {m['score']} |")
    lines.append('')
    lines.append('| 方法 | 胜负 | 大小 | 单双 | 三项等权综合 |')
    lines.append('|---|---:|---:|---:|---:|')
    for mid in order:
        s=batch['methods'][mid]['match_level']
        lines.append(f"| {names[mid]} | {s['result_record'][0]}/{s['result_record'][1]} = {pct(s['result'])} | "
                     f"{s['ou_record'][0]}/{s['ou_record'][1]} = {pct(s['ou'])} | "
                     f"{s['parity_record'][0]}/{s['parity_record'][1]} = {pct(s['parity'])} | {pct(s['composite'])} |")
    lines.append('')

lines.append('## 每10场累计核验')
lines.append('')
lines.append('| 累计场数 | 方法 | 胜负 | 大小 | 单双 | 三项等权综合 |')
lines.append('|---:|---|---:|---:|---:|---:|')
for c in cumulative:
    for mid in order:
        s=c['methods'][mid]['match_level']
        lines.append(f"| {c['matches']} | {names[mid]} | {pct(s['result'])} | {pct(s['ou'])} | {pct(s['parity'])} | {pct(s['composite'])} |")
    lines.append('|  |  |  |  |  |  |')

lines.append('')
lines.append('说明：胜负遇到真实平局时按V1.3原内核逻辑不计胜负分；大小、单双照常核验。这里的“综合”只是胜负/大小/单双三个命中率的简单等权平均，用来横向看同一方法在每10场里的总体一致性，不作为新权重。')
(base/'ten-match-raw-comparison.md').write_text('\n'.join(lines),encoding='utf-8')
print('\n'.join(lines))
