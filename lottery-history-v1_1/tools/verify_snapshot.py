import hashlib, json, re, sys
from pathlib import Path
from datetime import date, datetime
from zoneinfo import ZoneInfo
root=Path(sys.argv[1] if len(sys.argv)>1 else 'app/src/main/assets')
sources=json.loads((root/'verified_sources.json').read_text())
all_rows={}; manifest=['data_source_policy=OFFICIAL_ANNOUNCEMENTS_PARTIAL','generated_utc='+datetime.now(ZoneInfo('UTC')).isoformat()]
today=datetime.now(ZoneInfo('Asia/Shanghai')).date()
for name,n in [('kl8_desc.txt',20),('3d_desc.txt',3),('pl3_desc.txt',3),('pl5_desc.txt',5)]:
    rows={};dates=set()
    for line in (root/name).read_text().splitlines():
        a=line.split();assert len(a)==n+2,(name,line)
        issue,d=a[:2];nums=list(map(int,a[2:])); actual=date.fromisoformat(d)
        assert actual<=today and issue not in rows and d not in dates,(name,line)
        assert name+':'+issue in sources,(name,issue,'provenance missing')
        assert issue[:2]==d[2:4] if len(issue)==5 else issue[:4]==d[:4]
        assert all(1<=x<=80 for x in nums) and len(set(nums))==20 if n==20 else all(0<=x<=9 for x in nums)
        rows[issue]=(d,nums);dates.add(d)
    assert rows,(name,'empty')
    all_rows[name]=rows
    manifest.append(f'{name} count={len(rows)} first_date={min(dates)} last_date={max(dates)} sha256={hashlib.sha256((root/name).read_bytes()).hexdigest()}')
for issue,(d,nums) in all_rows['pl3_desc.txt'].items():
    assert issue in all_rows['pl5_desc.txt'] and (d,nums)==(all_rows['pl5_desc.txt'][issue][0],all_rows['pl5_desc.txt'][issue][1][:3]),issue
prizes={}
for line in (root/'kl8_prizes.tsv').read_text().splitlines():
    if not line or line.startswith('#'):continue
    issue,key,amount=line.split('\t');assert issue in all_rows['kl8_desc.txt']
    assert float(amount)>=0 and (issue,key) not in prizes
    prizes[issue,key]=float(amount)
for issue in all_rows['kl8_desc.txt']:
    for n in range(1,11):
        for hit in range(n+1):
            if n in (9,10) and n==hit:
                assert (issue,f'x{n}z{hit}') not in prizes
            else:assert (issue,f'x{n}z{hit}') in prizes
# Regression cases: zero-hit wins, normal losses, decimal award and new table.
issue=next(iter(all_rows['kl8_desc.txt']))
for key,expected in [('x1z1',4.5),('x1z0',0),('x10z0',2),('x10z8',720),('x9z7',225),('x8z6',80),('x7z7',8500),('x4z4',93)]:assert prizes[issue,key]==expected
manifest+=['pl3_relation=official_P5_first3_all_included_periods','kl8_fixed_awards=official_rules_effective_2025-12-30','kl8_floating_awards=UNRESOLVED_NO_INVENTED_AMOUNT','coverage=ONLY_INCLUDED_ISSUES_NOT_COMPLETE_HISTORY','validation=date_calendar+no_future+issue_year+unique_issues+unique_dates+numbers+official_provenance+P3_P5_relation+fixed_awards']
(root/'data_manifest.txt').write_text('\n'.join(manifest)+'\n')
print('\n'.join(manifest))
