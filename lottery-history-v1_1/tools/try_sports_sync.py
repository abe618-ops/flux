"""Try the national official API. Keep verified provincial seed if unreachable.
This never substitutes third-party or generated results for official records.
"""
import json, re, urllib.request
from pathlib import Path
from datetime import date
root=Path('app/src/main/assets')
def fetch(game):
    url=f'https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry?gameNo={game}&provinceId=0&pageSize=100&isVerify=1&pageNo=1'
    req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0','Referer':'https://m.lottery.gov.cn/','Accept':'application/json'})
    with urllib.request.urlopen(req,timeout=20) as f:data=json.load(f)
    out={}
    for row in data['value']['list']:
        issue=str(row['lotteryDrawNum']);d=row['lotteryDrawTime'][:10]
        nums=list(map(int,re.findall(r'\d+',row['lotteryDrawResult'])))
        assert len(nums)==(3 if game=='35' else 5) and all(0<=n<=9 for n in nums)
        assert len(issue)==5 and date.fromisoformat(d)<=date.today()
        if issue in out:assert out[issue]==(d,nums)
        out[issue]=(d,nums)
    assert len(out)>=30
    return out,url
try:
    p3,u3=fetch('35');p5,u5=fetch('350133')
    for issue,(d,ns) in p3.items():assert issue in p5 and p5[issue][0]==d and p5[issue][1][:3]==ns
except Exception as e:
    print('National official API unavailable; keeping the included verified provincial notices:',repr(e))
else:
    sources=json.loads((root/'verified_sources.json').read_text())
    for name,rows,url in [('pl3_desc.txt',p3,u3),('pl5_desc.txt',p5,u5)]:
        # Validate both new datasets completely before writing either.
        (root/name).write_text('\n'.join(' '.join([i,d]+list(map(str,ns))) for i,(d,ns) in sorted(rows.items(),reverse=True))+'\n')
        for i in rows:sources[name+':'+i]=url
    (root/'verified_sources.json').write_text(json.dumps(sources,ensure_ascii=False,indent=2))
    print('National official API synchronized:',len(p3),len(p5))
