"""수집 결과 3개를 합쳐 서버 DB(hyetaek.db)와 앱 API 응답(feed.json)을 만든다.
실행(ssu-steps 폴더에서):  python -m hyetaek.build_db
입력: step3_notices.json (SSU:catch), step4_results.json (AI), job_notices.json (JOB.ssu), program_notices.json (진로취업프로그램), site_notices.json (학과·학생회·청년몽땅)  -- 없는 파일은 건너뜀"""
from __future__ import annotations

import glob
import json
import os
import sys
from collections import Counter

from . import normalize as N
from . import store


def load(path):
    return json.load(open(path, encoding="utf-8")) if os.path.exists(path) else []


def merged(path):
    """이번 수집 결과 + 예전에 모아 둔 것(archive_*.json)을 url 기준으로 합친다. 같은 url이면 이번 것이 이긴다.
    수집 json은 매번 '최신 N개'로 덮어써지므로, 이렇게 모아 두지 않으면 예전 공지는 다시 처리(AI·규칙 개선)되지 않는다."""
    arch = "archive_" + path
    rows = {r["url"]: r for r in load(arch)}
    rows.update({r["url"]: r for r in load(path)})
    out = list(rows.values())
    if out:
        json.dump(out, open(arch, "w", encoding="utf-8"), ensure_ascii=False, indent=1, default=str)
    return out


def load_external(inbox=os.path.join("external", "inbox")):
    """팀원이 공통 형식으로 만든 파일(external/inbox/*.json)을 읽는다. 형식이 틀린 공지는 건너뛰고 이유를 출력한다."""
    files = sorted(glob.glob(os.path.join(inbox, "*.json")))
    if not files:
        return []
    import jsonschema
    v = jsonschema.Draft202012Validator(json.load(open("notice.schema.json", encoding="utf-8")))
    out = []
    for p in files:
        try:
            data = json.load(open(p, encoding="utf-8"))
        except (OSError, ValueError) as e:
            print(f"팀원 파일 읽기 실패: {p} ({e})")
            continue
        if isinstance(data, dict):
            data = data.get("items", [])
        ok = 0
        for x in data:
            errs = list(v.iter_errors(x))
            if not errs and x.get("id") != f"{x.get('source')}:{x.get('source_id')}":
                errs = ["id 는 'source:source_id' 여야 함"]
            if errs:
                e = errs[0]
                where = "/".join(map(str, getattr(e, "path", []))) or "(전체)"
                print(f"  [건너뜀] {p}: {str(x.get('id'))[:40]} → {where}: {getattr(e, 'message', e)}"[:200])
                continue
            out.append(N.from_common(x))
            ok += 1
        print(f"팀원 데이터 {os.path.basename(p)}: {ok}/{len(data)}건 반영")
    return out


def main(catch="step3_notices.json", ai="step4_results.json", job="job_notices.json", program="program_notices.json", db="hyetaek.db"):
    ai_by_url = {r["url"]: r for r in load(ai)}
    notices = [N.from_catch(r, ai_by_url.get(r["url"])) for r in merged(catch)]
    notices += [N.from_job(r, ai_by_url.get(r["url"])) for r in merged(job)]
    notices += [N.from_job(r, ai_by_url.get(r["url"])) for r in merged(program)]
    notices += [N.from_site(r, ai_by_url.get(r["url"])) for r in merged("site_notices.json")]   # AI대학·SW학부·학생회·청년몽땅
    notices += load_external()                                   # 팀원 수집분(교외·인스타). 공통 형식 그대로 받는다
    N.dedupe(notices)
    con = store.connect(db)
    res = Counter(store.upsert(con, n) for n in notices)
    con.commit()
    feed = store.feed_since(con, 0, 1000)
    json.dump(feed, open("feed.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"입력 {len(notices)}개 → 저장 {dict(res)} / 중복 {sum(1 for n in notices if n['dup_of'])}개 / 피드 {len(feed['items'])}개 (next_version={feed['next_version']})")
    print("주제별:", dict(Counter(n["topic"] for n in notices)))
    print("마감 종류:", dict(Counter(n["deadline"]["kind"] for n in notices)))
    print("대상 범위:", dict(Counter(n["audience"]["scope"] for n in notices)), "| 외국인 전용:",
          sum(1 for n in notices if n["audience"]["nationality"] == "FOREIGN_ONLY"), "| 학년 지정:", sum(1 for n in notices if n["audience"]["grades"]))
    return notices


if __name__ == "__main__":
    main(*sys.argv[1:])
