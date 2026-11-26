"""평강 수집기(SSUPICK/tools/ssupick_export_v1.py)를 그대로 쓰되, 실제 API에서 확인된 문제 2개만 고쳐서 실행한다.
평강 코드 파일은 건드리지 않는다(평강이 새 버전을 줘도 그대로 덮어쓰면 됨). 1_pg_live.py 가 SSUPICK 폴더에서 이 파일을 실행한다.

고친 것
1. 마이홈: 한 공고(pblancId)에 주택이 여러 개면 주택마다 원문 주소(pcUrl …&houseSn=N)가 달라
   '공고 공통 필드 불일치'로 전체가 멈춤 → 공통 주소(houseSn 뺀 것) 또는 첫 값을 쓴다.
   같은 주택번호에 내용이 조금 다른 행이 겹치면 '원본 충돌'로 멈춤 → 하나만 남긴다.
2. 온통청년: 4,000여 건을 100건씩 받다 어떤 페이지에서 서버가 HTTP 500을 줌 → 다시 시도하고,
   그래도 안 되면 그 페이지를 10건씩 나눠 받아 되는 것만 살린다(그래도 안 되는 10건만 빠짐)."""
from __future__ import annotations

import importlib.util
import re
import sys
import time
from pathlib import Path

ROOT = Path.cwd()
for d in ("src/collectors", "src/processors"):
    p = str(ROOT / d)
    if p not in sys.path:
        sys.path.insert(0, p)

# ── 1. 마이홈 ──
import myhome_normalize as MN                      # noqa: E402  (db14c 가 같은 모듈을 쓰므로 여기서 고치면 적용됨)

_orig_one = MN.one_value


def lenient_one_value(rows, field, required=True):
    vals = sorted({str(r.get(field) or "").strip() for r in rows if str(r.get(field) or "").strip()})
    if len(vals) > 1:
        if field in ("pcUrl", "mobileUrl"):
            base = {re.sub(r"[&?]houseSn=\d+", "", v) for v in vals}
            if len(base) == 1:
                return base.pop()
        return vals[0]
    return _orig_one(rows, field, required)


MN.one_value = lenient_one_value
_orig_group = MN.normalize_myhome_group


def lenient_group(rows, *a, **k):
    """같은 공고·같은 주택번호(houseSn)에 내용이 조금 다른 행이 여러 개면 '원본 충돌'로 멈춤 → 하나만 남긴다."""
    keep = {}
    for r in sorted(rows, key=MN.canonical):
        house = str(r.get("houseSn") if r.get("houseSn") is not None else "0").strip()
        keep.setdefault(house, r)
    return _orig_group(list(keep.values()), *a, **k)


MN.normalize_myhome_group = lenient_group

# ── 2. 온통청년 ──
spec = importlib.util.spec_from_file_location("ssupick_export_v1", ROOT / "tools" / "ssupick_export_v1.py")
EX = importlib.util.module_from_spec(spec)
spec.loader.exec_module(EX)


def fetch_ontong_live(page_size, max_pages, key):
    if not key:
        raise RuntimeError("YOUTH_POLICY_API_KEY 환경변수 누락")
    import requests
    rows, seen, lost = [], set(), 0

    def get(session, page, size):
        for wait in (0, 3, 8, 15):
            time.sleep(wait)
            try:
                r = session.get(EX.ONTONG_V2_URL, params={"apiKeyNm": key, "pageNum": page, "pageSize": size, "pageType": 1, "rtnType": "json"},
                                timeout=(10, 40), allow_redirects=False)
            except requests.RequestException:
                continue
            if r.status_code == 200:
                try:
                    res = r.json()["result"]
                    return res.get("youthPolicyList") or [], int((res.get("pagging") or {}).get("totCount") or 0)
                except (ValueError, KeyError, TypeError):
                    raise RuntimeError("온통청년 API 응답 형식이 다릅니다 (JSON v2 아님)") from None
            if r.status_code < 500:
                raise RuntimeError(f"온통청년 {page}페이지 HTTP {r.status_code}")
        return None, 0

    def add(chunk):
        for raw in chunk:
            pid = str(raw.get("plcyNo") or "").strip()
            if pid and pid not in seen:
                seen.add(pid)
                rows.append(raw)

    total, page = None, 0
    with requests.Session() as s:
        for page in range(1, max_pages + 1):
            chunk, tot = get(s, page, page_size)
            if chunk is None:                                # 계속 500 → 10건씩 나눠 받기
                small = 10
                for sub in range(1, page_size // small + 1):
                    c2, _ = get(s, (page - 1) * (page_size // small) + sub, small)
                    if c2 is None:
                        lost += small
                    else:
                        add(c2)
                print(f"온통청년 {page}페이지: 서버 오류로 나눠 받음 (빠진 것 최대 {lost}건)", flush=True)
                continue
            total = total or tot
            add(chunk)
            if len(chunk) < page_size or (total and page * page_size >= total):
                break
    if not rows:
        raise ValueError("온통청년 실제 수집 0건")
    return rows, {"pages": page, "raw_count": len(rows), "api_total": total, "lost_max": lost, "api_variant": "JSON_V2_LIVE_PATCHED"}


EX.fetch_ontong_live = fetch_ontong_live

if __name__ == "__main__":
    raise SystemExit(EX.main(sys.argv[1:]))
