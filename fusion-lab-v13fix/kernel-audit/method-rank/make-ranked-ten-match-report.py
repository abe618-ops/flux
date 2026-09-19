#!/usr/bin/env python3
import json
from pathlib import Path

base=Path('fusion-lab-v13fix/kernel-audit/method-rank')
src=json.load(open(base/'ten-match-raw-comparison.json',encoding='utf-8'))
pred=json.load(open(base/'frozen_method_predictions.json',encoding='utf-8'))
names=pred['kernel']['method_names']
order=pred['kernel']['method_order']

def pct(v):
    return '—' if v is None else f'{v:.1%}'

def den(rec, dim):
    if dim=='result': return rec['match_level']['result_record'][1]
    if dim=='ou': return rec['match_level']['ou_record'][1]
    if dim=='parity': return rec['match_level']['parity_record'][1]
    vals=[
        rec['match_level']['result_record'][1],
        rec['match_level']['ou_record'][1],
        rec['match_level']['parity_record'][1],
    ]
    return sum(vals)

def rate(rec, dim):
    return rec['match_level'][dim]

def rank_methods(methods, dim):
    rows=[]
    for mid,rec in methods.items():
        v=rate(rec,dim)
        rows.append((mid,v,den(rec,dim)))
    rows.sort(key=lambda x:(-1 if x[1] is None else -x[1], -x[2], order.index(x[0])))
    return rows

def rec_text(rec, dim):
    ml=rec['match_level']
    if dim=='result':
        h,n=ml['result_record']; return f'{h}/{n} = {pct(ml["result"])}'
    if dim=='ou':
        h,n=ml['ou_record']; return f'{h}/{n} = {pct(ml["ou"])}'
    if dim=='parity':
        h,n=ml['parity_record']; return f'{h}/{n} = {pct(ml["parity"])}'
    return pct(ml['composite'])

out={'note':'Pure observed hit-rate rankings. No prior weights, no method filtering, no historical rules.',
     'batch_rankings':[], 'cumulative_rankings':[]}

for b in src['batches']:
    entry={'batch':b['batch'],'matches':b['matches'],'rankings':{}}
    for dim in ('result','ou','parity','composite'):
        r=rank_methods(b['methods'],dim)
        entry['rankings'][dim]=[
            {'rank':i+1,'id':mid,'name':names[mid],'rate':v,'denominator':n}
            for i,(mid,v,n) in enumerate(r)
        ]
    out['batch_rankings'].append(entry)

for c in src['cumulative']:
    entry={'through_batch':c['through_batch'],'matches':c['matches'],'rankings':{}}
    for dim in ('result','ou','parity','composite'):
        r=rank_methods(c['methods'],dim)
        entry['rankings'][dim]=[
            {'rank':i+1,'id':mid,'name':names[mid],'rate':v,'denominator':n}
            for i,(mid,v,n) in enumerate(r)
        ]
    out['cumulative_rankings'].append(entry)

json.dump(out,open(base/'ten-match-ranked-statistics.json','w',encoding='utf-8'),ensure_ascii=False,indent=2)

lines=[]
lines.append('# V1.3 每10场原始命中率排序与完整统计')
lines.append('')
lines.append('原则：不使用旧权重、不筛选方法、不人为判断倾向。每10场开盒后，仅按该组真实命中率分别排序：胜负、大小球、单双、三项等权综合。随后再做10/20/30…/100场累计排序。')
lines.append('')

for b in src['batches']:
    lines.append(f'## 第{b["batch"]}组：10场')
    lines.append('')
    lines.append('比赛：' + '；'.join([f'{m["home_team"]}-{m["away_team"]} {m["score"]}' for m in b['matches']]))
    lines.append('')
    for dim,title in [('result','胜负排名'),('ou','大小球排名'),('parity','单双排名'),('composite','综合排名')]:
        lines.append(f'### {title}')
        lines.append('')
        lines.append('| 排名 | 方法 | 命中率 |')
        lines.append('|---:|---|---:|')
        for i,(mid,v,n) in enumerate(rank_methods(b['methods'],dim),1):
            lines.append(f'| {i} | {names[mid]} | {rec_text(b["methods"][mid],dim)} |')
        lines.append('')

lines.append('## 每10场累计排名')
lines.append('')
for c in src['cumulative']:
    lines.append(f'### 累计{c["matches"]}场')
    lines.append('')
    lines.append('| 排名 | 胜负 | 大小球 | 单双 | 综合 |')
    lines.append('|---:|---|---|---|---|')
    ranks={dim:rank_methods(c['methods'],dim) for dim in ('result','ou','parity','composite')}
    for i in range(len(order)):
        cells=[]
        for dim in ('result','ou','parity','composite'):
            mid,v,n=ranks[dim][i]
            cells.append(f'{names[mid]} {rec_text(c["methods"][mid],dim)}')
        lines.append(f'| {i+1} | {cells[0]} | {cells[1]} | {cells[2]} | {cells[3]} |')
    lines.append('')

# Cross-batch placement frequencies: descriptive only, not a new weight.
lines.append('## 10个批次的名次出现次数')
lines.append('')
lines.append('只统计每个方法在10个独立10场批次中进入前1、前3、前5的次数，用于观察名次是否反复出现；不把它自动转成权重。')
lines.append('')
for dim,title in [('result','胜负'),('ou','大小球'),('parity','单双'),('composite','综合')]:
    counts={mid:{'top1':0,'top3':0,'top5':0,'sum_rank':0} for mid in order}
    for b in out['batch_rankings']:
        for row in b['rankings'][dim]:
            mid=row['id']; rk=row['rank']
            counts[mid]['sum_rank']+=rk
            counts[mid]['top1']+=int(rk==1)
            counts[mid]['top3']+=int(rk<=3)
            counts[mid]['top5']+=int(rk<=5)
    ordered=sorted(order,key=lambda mid:(-counts[mid]['top3'],-counts[mid]['top5'],counts[mid]['sum_rank']/10,order.index(mid)))
    lines.append(f'### {title}')
    lines.append('')
    lines.append('| 方法 | 第1名次数 | 前3次数 | 前5次数 | 10组平均名次 |')
    lines.append('|---|---:|---:|---:|---:|')
    for mid in ordered:
        x=counts[mid]
        lines.append(f'| {names[mid]} | {x["top1"]} | {x["top3"]} | {x["top5"]} | {x["sum_rank"]/10:.1f} |')
    lines.append('')

lines.append('说明：真实平局按V1.3原胜负逻辑不计胜负分，因此不同方法在胜负栏的有效场数可能略有差异；大小球和单双按实际总进球核验。综合仅为当组胜负/大小/单双三项命中率简单等权平均，不代表正式权重。')

(base/'ten-match-ranked-statistics.md').write_text('\n'.join(lines),encoding='utf-8')
print('\n'.join(lines))
