#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build-time official lottery history sync + strict validation.
Sources:
- China Welfare Lottery official API: 福彩3D / 快乐8
- China Sports Lottery official API: 排列3 / 排列5
Outputs normalized UTF-8 asset files and KL8 per-draw prize map.
Any integrity failure exits non-zero so the APK is not built.
"""
from __future__ import annotations
import json, os, re, subprocess, sys, tempfile, time
from pathlib import Path
from urllib.parse import urlencode
from datetime import datetime, date

ASSETS = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("app/src/main/assets")
ASSETS.mkdir(parents=True, exist_ok=True)
UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"
UA_MOBILE = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
TODAY = date.today().isoformat()

class SyncError(RuntimeError): pass

def curl(url: str, *, cookie: str|None=None, save_cookie: str|None=None, referer: str|None=None,
         ua: str=UA_DESKTOP, accept: str="application/json", retries: int=4) -> str:
    cmd = ["curl","-fsSL","--connect-timeout","15","--max-time","60","--retry",str(retries),"--retry-delay","2",
           "-A",ua,"-H",f"Accept: {accept}"]
    if referer: cmd += ["-e", referer]
    if cookie: cmd += ["-b", cookie]
    if save_cookie: cmd += ["-c", save_cookie]
    cmd += [url]
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        raise SyncError(f"curl failed {p.returncode}: {p.stderr.decode('utf-8','ignore')[-500:]}")
    return p.stdout.decode("utf-8", "replace")

def clean_date(v) -> str:
    s = str(v or "")
    m = re.search(r"(20\d{2})[-/](\d{1,2})[-/](\d{1,2})", s)
    if not m: return ""
    return f"{m.group(1)}-{int(m.group(2)):02d}-{int(m.group(3)):02d}"

def clean_nums(v, need: int, lo: int, hi: int) -> list[int]:
    if isinstance(v, list): toks = v
    else: toks = re.findall(r"\d+", str(v or ""))
    nums = [int(x) for x in toks]
    if len(nums) != need or any(x < lo or x > hi for x in nums):
        raise SyncError(f"bad numbers need={need}: {v!r}")
    if need == 20 and len(set(nums)) != 20:
        raise SyncError(f"KL8 duplicated numbers: {v!r}")
    return nums

def fetch_cwl(name: str, need: int, lo: int, hi: int):
    cookie = str(Path(tempfile.gettempdir()) / f"cwl-{name}.cookie")
    base = "https://www.cwl.gov.cn"
    # establish official-site cookies first; current CWL gateway often rejects cookie-less calls
    for landing in [f"{base}/ygkj/wqkjgg/{'fc3d' if name=='3d' else 'kl8'}/", f"{base}/ygkj/wqkjgg/", base+"/"]:
        try:
            curl(landing, save_cookie=cookie, referer=base+"/", accept="text/html,*/*")
            break
        except Exception:
            continue
    out = []
    seen = set()
    total_hint = None
    page = 1
    page_size = 100
    while page <= 500:
        q = {
            "name": name, "issueCount": "", "issueStart": "", "issueEnd": "",
            "dayStart": "", "dayEnd": "", "pageNo": page, "pageSize": page_size,
            "week": "", "systemType": "PC"
        }
        url = base + "/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?" + urlencode(q)
        data = None
        last = None
        for attempt in range(5):
            try:
                raw = curl(url, cookie=cookie, save_cookie=cookie, referer=base+"/ygkj/wqkjgg/", retries=2)
                data = json.loads(raw)
                if isinstance(data, dict) and isinstance(data.get("result"), list): break
                last = raw[:300]
            except Exception as e:
                last = repr(e)
            time.sleep(min(2+attempt, 6))
            try: curl(base+"/ygkj/wqkjgg/", save_cookie=cookie, referer=base+"/", accept="text/html,*/*", retries=1)
            except Exception: pass
        if not isinstance(data, dict) or not isinstance(data.get("result"), list):
            raise SyncError(f"CWL {name} page {page} invalid response: {last}")
        rows = data.get("result") or []
        if total_hint is None:
            try: total_hint = int(data.get("total") or 0)
            except: total_hint = 0
        if not rows: break
        new_count = 0
        for item in rows:
            issue = str(item.get("code") or "").strip()
            d = clean_date(item.get("date"))
            if not issue or not d or d > TODAY: continue
            if issue in seen: continue
            nums = clean_nums(item.get("red"), need, lo, hi)
            out.append({"issue":issue,"date":d,"nums":nums,"prizegrades":item.get("prizegrades") or []})
            seen.add(issue); new_count += 1
        print(f"CWL {name}: page {page}, rows={len(rows)}, new={new_count}, total={len(out)}, hint={total_hint}")
        if new_count == 0: break
        if total_hint and len(out) >= total_hint: break
        page += 1
    if not out: raise SyncError(f"CWL {name}: no data")
    out.sort(key=lambda x:(x["date"], x["issue"]), reverse=True)
    return out, total_hint or len(out)

def fetch_sporttery(game_no: str, need: int):
    base = "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry"
    out=[]; seen=set(); page=1; total_hint=None; pages_hint=None
    while page <= 500:
        q = {"gameNo":game_no,"provinceId":"0","pageSize":"100","isVerify":"1","pageNo":str(page)}
        url=base+"?"+urlencode(q)
        data=None; last=None
        for attempt in range(5):
            try:
                raw=curl(url, referer="https://m.lottery.gov.cn/", ua=UA_MOBILE, retries=2)
                data=json.loads(raw)
                val=data.get("value") if isinstance(data,dict) else None
                if isinstance(val,dict) and isinstance(val.get("list"),list): break
                last=raw[:300]
            except Exception as e: last=repr(e)
            time.sleep(min(1+attempt,5))
        if not isinstance(data,dict) or not isinstance(data.get("value"),dict):
            raise SyncError(f"Sporttery {game_no} page {page} invalid: {last}")
        val=data["value"]; rows=val.get("list") or []
        if total_hint is None:
            try: total_hint=int(val.get("total") or 0)
            except: total_hint=0
            try: pages_hint=int(val.get("pages") or 0)
            except: pages_hint=0
        if not rows: break
        new_count=0
        for item in rows:
            issue=str(item.get("lotteryDrawNum") or "").strip()
            d=clean_date(item.get("lotteryDrawTime"))
            if not issue or not d or d>TODAY or issue in seen: continue
            nums=clean_nums(item.get("lotteryDrawResult"),need,0,9)
            out.append({"issue":issue,"date":d,"nums":nums}); seen.add(issue); new_count+=1
        print(f"Sporttery {game_no}: page {page}, rows={len(rows)}, new={new_count}, total={len(out)}, hint={total_hint}, pages={pages_hint}")
        if new_count==0: break
        if total_hint and len(out)>=total_hint: break
        if pages_hint and page>=pages_hint: break
        page+=1
    if not out: raise SyncError(f"Sporttery {game_no}: no data")
    out.sort(key=lambda x:(x["date"],x["issue"]), reverse=True)
    return out, total_hint or len(out)

def write_history(name:str, rows:list[dict]):
    p=ASSETS/name
    with p.open("w",encoding="utf-8",newline="\n") as f:
        for r in rows:
            nums=" ".join(f"{x:02d}" if len(r["nums"])==20 else str(x) for x in r["nums"])
            f.write(f"{r['issue']} {r['date']} {nums}\n")
    return p

def normalize_money(v):
    s=str(v or "").replace(",","").strip()
    if not s or s in {"-","--"}: return None
    m=re.search(r"-?\d+(?:\.\d+)?",s)
    if not m:return None
    x=float(m.group())
    return x if x>=0 else None

def write_kl8_prizes(rows:list[dict]):
    p=ASSETS/"kl8_prizes.tsv"
    draw_with_grades=0; newest_types=0
    with p.open("w",encoding="utf-8",newline="\n") as f:
        f.write("# issue\ttype\tamount_per_bet_yuan\n")
        for idx,r in enumerate(rows):
            n=0
            for g in r.get("prizegrades") or []:
                t=str(g.get("type") or "").strip().lower()
                money=normalize_money(g.get("typemoney"))
                if not re.fullmatch(r"x(?:10|[1-9])z(?:10|[0-9])",t) or money is None: continue
                f.write(f"{r['issue']}\t{t}\t{money:g}\n"); n+=1
            if n: draw_with_grades+=1
            if idx==0:newest_types=n
    print(f"KL8 prize map: draws with prizegrades={draw_with_grades}/{len(rows)}, newest types={newest_types}")
    # Official KL8 response should expose a broad prizegrade table, not just one or two rows.
    if newest_types < 25:
        raise SyncError(f"KL8 official prize table incomplete on newest draw: only {newest_types} types")
    if draw_with_grades < max(100, int(len(rows)*0.95)):
        raise SyncError(f"KL8 prize coverage too low: {draw_with_grades}/{len(rows)}")
    return p

def validate_rows(label, rows, need, lo, hi, minimum):
    if len(rows)<minimum: raise SyncError(f"{label}: only {len(rows)} rows (<{minimum})")
    issues=set(); dates=set()
    for r in rows:
        if r['issue'] in issues: raise SyncError(f"{label}: duplicate issue {r['issue']}")
        issues.add(r['issue'])
        if not re.fullmatch(r"20\d{2}-\d{2}-\d{2}",r['date']): raise SyncError(f"{label}: bad date {r['date']}")
        if r['date']>TODAY: raise SyncError(f"{label}: future draw {r['date']}")
        if len(r['nums'])!=need or any(x<lo or x>hi for x in r['nums']): raise SyncError(f"{label}: bad nums {r}")
        if need==20 and len(set(r['nums']))!=20: raise SyncError(f"{label}: duplicate KL8 nums {r['issue']}")
        dates.add(r['date'])
    print(f"VALID {label}: rows={len(rows)} newest={rows[0]['issue']} {rows[0]['date']} {' '.join(map(str,rows[0]['nums']))}")

def validate_pl_relation(pl3, pl5):
    p3={r['date']:r for r in pl3}; p5={r['date']:r for r in pl5}; common=sorted(set(p3)&set(p5), reverse=True)
    if len(common)<1000: raise SyncError(f"P3/P5 common dates too few: {len(common)}")
    bad=[]
    for d in common:
        if p3[d]['nums'] != p5[d]['nums'][:3]: bad.append((d,p3[d],p5[d]))
    if bad: raise SyncError(f"P3/P5 official invariant failed on {len(bad)} dates; first={bad[0]}")
    print(f"VALID P3=P5 first3 on all {len(common)} common official draw dates")

def main():
    # Welfare: official CWL
    d3,_=fetch_cwl("3d",3,0,9)
    kl8,_=fetch_cwl("kl8",20,1,80)
    # Sports: official Sporttery. Current official ids: P3=35, P5=350133; fall back to legacy P5=37 if needed.
    pl3,_=fetch_sporttery("35",3)
    try: pl5,_=fetch_sporttery("350133",5)
    except Exception as e:
        print(f"P5 gameNo=350133 failed: {e}; trying legacy 37")
        pl5,_=fetch_sporttery("37",5)

    validate_rows("福彩3D",d3,3,0,9,3000)
    validate_rows("快乐8",kl8,20,1,80,1000)
    validate_rows("排列3",pl3,3,0,9,3000)
    validate_rows("排列5",pl5,5,0,9,3000)
    validate_pl_relation(pl3,pl5)

    write_history("3d_desc.txt",d3)
    write_history("kl8_desc.txt",kl8)
    write_history("pl3_desc.txt",pl3)
    write_history("pl5_desc.txt",pl5)
    write_kl8_prizes(kl8)

    manifest = [
        "data_source_policy=OFFICIAL_ONLY",
        "generated_utc="+datetime.utcnow().replace(microsecond=0).isoformat()+"Z",
        f"fc3d_count={len(d3)} newest={d3[0]['issue']} date={d3[0]['date']}",
        f"kl8_count={len(kl8)} newest={kl8[0]['issue']} date={kl8[0]['date']}",
        f"pl3_count={len(pl3)} newest={pl3[0]['issue']} date={pl3[0]['date']}",
        f"pl5_count={len(pl5)} newest={pl5[0]['issue']} date={pl5[0]['date']}",
        "cwl=https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice",
        "sporttery=https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry",
        "validation=counts+ranges+unique_issues+no_future_dates+KL8_unique_numbers+P3_equals_P5_first3_all_common_dates+KL8_prizegrade_coverage"
    ]
    (ASSETS/"data_manifest.txt").write_text("\n".join(manifest)+"\n",encoding="utf-8")
    print("\n".join(manifest))

if __name__=="__main__":
    try: main()
    except Exception as e:
        print("FATAL DATA VALIDATION:",repr(e),file=sys.stderr)
        raise
