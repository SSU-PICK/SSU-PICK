"""평강 수집 결과(SSU:PICK Notice Collection v1.0) → 우리 공통 형식(notice.schema.json) 변환기.

서버가 수집할 때마다(collect_all.py) 자동으로 실행된다. 직접 돌려 볼 때 (server-lab 폴더에서):
    python external/collectors/2_pg_convert.py
입력: external/pg_data/ 안의 평강 파일 (없는 파일은 건너뜀)
    lh_myhome_snapshot.json     LH·마이홈 (11/17 평강 DB 스냅샷: records[].payload)
    lh_live.json, myhome_live.json, ontongyouth_live.json   실시간 수집 결과 (1_pg_live.py 가 만든다. 스냅샷보다 우선)
    ontongyouth.json            온통청년 (v1.0: notices[])
    mongddang_list.json         청년몽땅 목록 (v1.0)  + mongddang_detail.json (본문·신청기간)
    school_notices.json         학교: 총학·AI SW 학생회 링크트리, AI대학·SW학부 공지 (v1.0)
    ai_college_detail.json      AI대학 공지 본문
    *.json (그 밖의 v1.0 파일)   새 출처가 생기면 이름만 맞춰 넣으면 일반 규칙으로 변환
출력: external/inbox/pg_<출처>.json  → build_db 가 읽어서 DB에 넣는다 (같은 내용이면 버전이 안 바뀐다)

앱(API) 응답 모양은 바뀌지 않는다. source 값과 공지 수만 늘어난다.
평강 데이터 대부분이 '검토 전(HOLD/UNVERIFIED)'이라 needs_review=true 로 넣는다 → 앱은 '원문 확인 필요'를 표시."""
from __future__ import annotations

import difflib
import glob
import hashlib
import json
import os
import re
import sys
from datetime import date, timedelta

DATA = os.path.join("external", "pg_data")
INBOX = os.path.join("external", "inbox")

# ───── 설정 (필요하면 여기만 고친다) ─────
SKIP_LH_HIDDEN = True          # LH·마이홈 중 평강이 '목록에서 숨김(접수마감 등)'으로 판정한 공고는 넣지 않는다
SCHOOL_MAX_AGE_DAYS = 180      # 학교 공지: 이보다 오래된 글은 넣지 않는다 (우리 슈캐치 수집 규칙과 같음)
EXCLUDE_SOURCES: set[str] = set()   # 예: {"ontongyouth"} → 그 출처는 통째로 빼기
PRIVACY_TITLE = re.compile(r"선발자|합격자|선발\s*결과|당첨자|명단")   # 이름·학번이 실릴 수 있는 글 → 제목·링크만

CAMPUS = {"ssu_council", "ssu_aisw", "ssu_ai_college", "ssu_sw_official", "instagram"}

TOPIC = {"HOUSING": "주거", "YOUTH_POLICY": "청년지원", "SCHOLARSHIP": "장학", "CAREER": "채용·인턴",
         "PROGRAM": "비교과·프로그램", "EVENT": "비교과·프로그램", "VOLUNTEER": "봉사",
         "STUDENT_WELFARE": "기타", "OTHER": "기타"}
# 제목 단어로 주제를 다듬는다 (학교 공지는 평강 분류가 거칠어서). 앞에 있는 규칙이 먼저.
TITLE_TOPIC = [(r"장학|학자금", "장학"), (r"선거|투표|공약", "기타"),
               (r"졸업|교과과정|수강|전과|소속변경|학·석사|학석사|연계과정|이수|학점|복학|휴학", "학사"),
               (r"학부연구생|채용|인턴|연구실", "채용·인턴"), (r"해외|교환|산업시찰|연수", "해외·교환"),
               (r"봉사", "봉사"), (r"공모전|대외활동|서포터즈", "공모전·대외활동")]
DL_KIND = {"APPLICATION", "SCHOOL_NOMINATION", "DOCUMENT", "INTERVIEW", "EVENT", "OTHER"}
STATUS_WORDS = [("미취업", "JOB_SEEKER"), ("구직", "JOB_SEEKER"), ("재학", "ENROLLED"), ("휴학", "LEAVE"),
                ("졸업예정", "GRADUATING"), ("졸업", "GRADUATE")]
REGION_RE = re.compile(r"^(서울특별시|부산광역시|대구광역시|인천광역시|광주광역시|대전광역시|울산광역시|세종특별자치시|"
                       r"경기도|강원특별자치도|강원도|충청북도|충청남도|전북특별자치도|전라북도|전라남도|경상북도|경상남도|"
                       r"제주특별자치도|전남광주통합특별시)(?:\s+(\S+?[시군구]))?(?=\s|$)")


# ───── 작은 도구 ─────
def load(name):
    p = os.path.join(DATA, name)
    if not os.path.exists(p):
        return None
    return json.load(open(p, encoding="utf-8"))


def notices_of(d):
    if d is None:
        return []
    if isinstance(d, list):
        return d
    if "records" in d:                                   # 운영 DB 스냅샷
        return [r.get("payload") or r for r in d["records"]]
    return d.get("notices") or d.get("items") or []


def safe_id(s: str) -> str:
    """우리 서버 규칙: 영문·숫자·-_ 40자 이하. 아니면 짧은 해시."""
    s = str(s or "")
    if s and s.isascii() and len(s) <= 40 and s.replace("-", "").replace("_", "").isalnum():
        return s
    return hashlib.sha1(s.encode()).hexdigest()[:12]


def at_of(date_s, at_s=None, end_time=None, start=False):
    """→ ('2026-11-13T23:59:00+09:00', 'DAY'|'MINUTE')"""
    if at_s:
        s = str(at_s).replace(" ", "T")
        if len(s) >= 16:
            return s[:16] + ":00+09:00", "MINUTE"
        date_s = s[:10]
    if not date_s or not re.match(r"\d{4}-\d{2}-\d{2}$", str(date_s)[:10]):
        return None, None
    d = str(date_s)[:10]
    if end_time and re.match(r"\d{2}:\d{2}$", end_time) and end_time not in ("00:00", "23:59"):
        return f"{d}T{end_time}:00+09:00", "MINUTE"
    return f"{d}T{'00:00' if start else '23:59'}:00+09:00", "DAY"


def dl(kind, label, date_s=None, at_s=None, end_time=None, start=False):
    at, prec = at_of(date_s, at_s, end_time, start)
    if not at:
        return None
    return {"kind": kind if kind in DL_KIND else ("EVENT" if kind == "ANNOUNCEMENT" else "OTHER"),
            "label": (label or "마감")[:60], "date": at[:10], "at": at, "precision": prec}


def topic_of(x):
    t = TOPIC.get(x.get("topic"), "기타")
    if x.get("source") in CAMPUS or t == "기타":
        for pat, name in TITLE_TOPIC:
            if re.search(pat, x.get("title") or ""):
                return name
    if t == "청년지원" and any("주거" in str(tag) or "주택" in str(tag) for tag in x.get("tags") or []):
        return "주거"
    return t


def int_or_none(v):
    try:
        v = int(v)
    except (TypeError, ValueError):
        return None
    return v if 0 < v < 100 else None                    # 0 = '제한 없음/모름'


def region_of(org: str):
    m = REGION_RE.match((org or "").strip())
    if not m:
        return []
    return [m.group(1) + (" " + m.group(2) if m.group(2) else "")]


def eligibility(x):
    e = x.get("eligibility") or {}
    ex = x.get("extra") or {}
    src = ex.get("eligibility_source") or {}
    other = [str(c).strip()[:300] for c in e.get("other_conditions") or [] if str(c).strip()]
    raw = (e.get("raw_text") or "").strip()
    if raw and raw[:300] not in other:
        other.append(raw[:300])
    if src.get("additional_conditions") and src["additional_conditions"][:300] not in other:
        other.append(src["additional_conditions"][:300])
    majors = e.get("majors")
    if majors:
        other.append("전공: " + (", ".join(map(str, majors)) if isinstance(majors, list) else str(majors)))
    statuses = []
    for word in [e.get("employment_status")] if isinstance(e.get("employment_status"), str) else (e.get("employment_status") or []):
        for k, v in STATUS_WORDS:
            if k in str(word) and v not in statuses:
                statuses.append(v)
    regions = e.get("regions") if isinstance(e.get("regions"), list) else []
    if not regions:
        regions = region_of((ex.get("organizations") or {}).get("supervising") or "") or \
            ([ex["region"]] if isinstance(ex.get("region"), str) and ex.get("region") else [])
    nat = {"DOMESTIC": "DOMESTIC_ONLY", "DOMESTIC_ONLY": "DOMESTIC_ONLY", "FOREIGN": "FOREIGN_ONLY",
           "FOREIGN_ONLY": "FOREIGN_ONLY"}.get(e.get("nationality"))
    grades = [g for g in e.get("grades") or [] if isinstance(g, int) and 1 <= g <= 6]
    min_age = int_or_none(e.get("min_age")) or int_or_none(src.get("age_min"))
    max_age = int_or_none(e.get("max_age")) or int_or_none(src.get("age_max"))
    if src.get("age_limit_flag") == "Y":                 # 온통청년: 'Y' = 나이 제한 없음
        min_age = max_age = None
    out = {"scope": "ALL", "colleges": [], "depts": [], "grades": grades, "levels": [], "statuses": statuses,
           "nationality": nat, "min_age": min_age, "max_age": max_age, "regions": [str(r) for r in regions][:10],
           "min_gpa": None, "other_conditions": other[:8],
           "confidence": "medium" if (min_age or max_age or regions or other) else "low"}
    return out


def content_kind(x, body):
    k = x.get("content_kind")
    if k == "IMAGE":
        return "이미지"
    if k in ("ATTACHMENT", "FILE"):
        return "첨부만"
    return "TEXT" if (body or x.get("summary")) else "본문없음"


def clean_mongddang_body(t: str) -> str:
    """청년몽땅 상세 글에서 사이트 메뉴 글자를 떼어 낸다."""
    if not t:
        return ""
    i = t.find("상세정보")
    t = t[i + 4:] if i >= 0 else t
    return re.sub(r"\s*목록\s*$", "", t).strip()


# ───── 공통 변환 ─────
def convert(x, *, body=None, deadlines_extra=(), extra_add=None):
    src = x["source"]
    sid = safe_id(x.get("source_id") or x.get("id", "").split(":")[-1])
    body = body if body is not None else (x.get("body") or None)
    dls = []
    for d in x.get("deadlines") or []:
        y = dl(d.get("kind"), d.get("label"), d.get("date"), d.get("at"))
        if y:
            dls.append(y)
    for ev in x.get("events") or []:
        if ev.get("apply_end_at"):
            y = dl("APPLICATION", (ev.get("label") or "") + " 신청 마감", at_s=ev["apply_end_at"]) if len(str(ev["apply_end_at"])) >= 16 \
                else dl("APPLICATION", (ev.get("label") or "") + " 신청 마감", ev["apply_end_at"])
            if y:
                dls.append(y)
        if ev.get("start_at"):
            y = dl("EVENT", ev.get("label") or "행사", str(ev["start_at"])[:10],
                   ev["start_at"] if len(str(ev["start_at"])) >= 16 else None)
            if y:
                dls.append(y)
    dls += [d for d in deadlines_extra if d]
    seen, uniq = set(), []
    for d in sorted(dls, key=lambda d: (d["at"], d["kind"])):
        k = (d["kind"], d["at"])
        if k not in seen:
            seen.add(k)
            uniq.append(d)
    dls = uniq
    apps = [d["at"] for d in dls if d["kind"] in ("APPLICATION", "SCHOOL_NOMINATION", "DOCUMENT")]
    others = [d["at"] for d in dls if d["kind"] == "OTHER"]
    deadline_at = max(apps) if apps else (max(others) if others else None)
    st = x.get("deadline_status")
    status = "FIXED" if deadline_at else ("NONE" if st == "NOT_APPLICABLE" else "UNKNOWN")
    ver = (x.get("verification") or {}).get("status")
    ex = x.get("extra") or {}
    extra = {"publisher": x.get("publisher"), "apply_url": x.get("apply_url"), "tags": x.get("tags") or [],
             "review_status": "VERIFIED" if ver == "VERIFIED" else "HOLD", "collected_by": "평강"}
    for k in ("housing_type", "region", "notice_status", "application_method"):
        if ex.get(k):
            extra[k] = ex[k]
    if extra_add:
        extra.update(extra_add)
    extra = {k: v for k, v in extra.items() if v not in (None, [], "")}
    atts = []
    for a in x.get("attachments") or []:
        if isinstance(a, str):
            atts.append({"name": None, "url": a})
        elif isinstance(a, dict) and a.get("url"):
            atts.append({"name": a.get("name") or a.get("title"), "url": a["url"]})
    summary = (x.get("summary") or "").strip() or None
    return {
        "id": f"{src}:{sid}", "source": src, "origin": "CAMPUS" if src in CAMPUS else "EXTERNAL", "source_id": sid,
        "url": x.get("original_url") or x.get("apply_url") or x.get("url"), "title": (x.get("title") or "").strip(),
        "topic": topic_of(x), "posted_at": (x.get("published_at") or "")[:10] or None,
        "updated_at": x.get("collected_at"),
        "deadline_status": status, "deadline_at": deadline_at, "deadlines": dls,
        "eligibility": eligibility(x), "summary": summary, "body": body or None,
        "needs_review": ver != "VERIFIED" or st == "NEEDS_REVIEW",
        "content_kind": content_kind(x, body), "attachments": atts, "extra": extra,
    }


# ───── 출처별 손질 ─────
LH_CLOSED = ("접수마감", "마감", "공고마감")


def lh_myhome():
    """스냅샷(11/17 평강 DB) + 실시간 수집(1_pg_live.py 결과). 같은 공고면 실시간 것이 이긴다."""
    rows = {}
    for x in notices_of(load("lh_myhome_snapshot.json")):
        rows[x["id"]] = ("snapshot", x)
    for name in ("lh_live.json", "myhome_live.json"):
        for x in notices_of(load(name)):
            rows[x["id"]] = ("live", x)
    out, hidden = [], 0
    for kind, x in rows.values():
        ex = x.get("extra") or {}
        rv = ex.get("application_review") or {}
        raw = ex.get("raw_lh_list") or {}
        status = str(ex.get("notice_status") or raw.get("PAN_SS") or "")
        if SKIP_LH_HIDDEN and (str(rv.get("display_recommendation", "")).startswith("HIDE")
                               or (kind == "live" and status in LH_CLOSED)):
            hidden += 1
            continue
        extra_dl = []
        if rv.get("application_end_date"):
            extra_dl.append(dl("APPLICATION", "신청 마감", rv["application_end_date"]))
        if rv.get("application_start_date"):
            extra_dl.append(dl("EVENT", "신청 시작", rv["application_start_date"], start=True))
        clsg = str(raw.get("CLSG_DT") or "").strip()            # 실시간 LH: 목록의 공고 마감일 (평강 스냅샷과 같은 규칙)
        if clsg and not x.get("deadlines"):
            m = re.match(r"(\d{4})[.\-]?(\d{2})[.\-]?(\d{2})", clsg)
            if m:
                extra_dl.append(dl("OTHER", "공고 마감(신청 마감 여부 확인 필요)", "-".join(m.groups())))
        out.append(convert(x, deadlines_extra=extra_dl))
    if hidden:
        print(f"  LH·마이홈: 접수마감 등 '숨김' 판정 {hidden}건 제외")
    return out


ONTONG_REGIONS = {"서울특별시"}          # 실시간 온통청년: 이 지역 + 전국(중앙부처) 정책만 넣는다


def ontong():
    rows = {x["id"]: x for x in notices_of(load("ontongyouth.json"))}
    live, kept = notices_of(load("ontongyouth_live.json")), 0
    today = date.today().isoformat()
    for x in live:                                                # 실시간 수집이 있으면 그것이 이긴다 (전국 수천 건 → 걸러서)
        y = convert(x)
        regs = [r.split()[0] for r in y["eligibility"]["regions"]]
        if regs and not set(regs) & ONTONG_REGIONS:
            continue
        if y["deadline_at"] and y["deadline_at"][:10] < today:
            continue
        rows[x["id"]] = x
        kept += 1
    if live:
        print(f"  온통청년 실시간: {len(live)}건 중 서울·전국 + 마감 안 지난 {kept}건 반영")
    return [convert(x) for x in rows.values()]


def crawled_sources():
    """우리 크롤러(site_collect.py)가 직접 받는 출처는 평강 파일을 쓰지 않는다 (같은 공지가 두 번 들어가지 않게)."""
    s = set()
    for f in ("site_notices.json", "archive_site_notices.json"):
        try:
            s |= {r.get("source") for r in json.load(open(f, encoding="utf-8"))}
        except (OSError, ValueError):
            pass
    return s - {None}


def mongddang():
    det = {str(d.get("source_id")): d for d in notices_of(load("mongddang_detail.json"))}
    out = []
    for x in notices_of(load("mongddang_list.json")):
        d = det.get(str(x.get("source_id"))) or {}
        if d.get("exclusion_hint"):
            continue
        extra_dl = []
        if d.get("period_end"):
            extra_dl.append(dl("APPLICATION", "신청 마감", d["period_end"], end_time=d.get("period_end_time")))
        if d.get("period_start"):
            extra_dl.append(dl("EVENT", "신청 시작", d["period_start"], start=True))
        y = convert(x, body=clean_mongddang_body(d.get("body_candidate") or "")[:6000] or None, deadlines_extra=extra_dl,
                    extra_add={"publisher": d.get("publisher_hint")})
        if not y["eligibility"]["regions"]:
            y["eligibility"]["regions"] = region_of(d.get("publisher_hint") or "")
        if d.get("title"):
            y["title"] = d["title"]
        out.append(y)
    return out


def school():
    det = {str(d.get("id")): d for d in notices_of(load("ai_college_detail.json"))}
    rows = notices_of(load("school_notices.json"))
    cutoff = (date.today() - timedelta(days=SCHOOL_MAX_AGE_DAYS)).isoformat()
    keep, skipped = [], {"오래됨": 0, "명단(제목만)": 0, "중복": 0}
    for x in rows:
        if x.get("published_at") and x["published_at"][:10] < cutoff:
            skipped["오래됨"] += 1
            continue
        if PRIVACY_TITLE.search(x.get("title") or ""):          # 명단 글: 제목·링크만 남기고 본문·첨부는 버린다
            skipped["명단(제목만)"] += 1
            x = {**x, "body": None, "attachments": [], "summary": "선발·합격 결과를 알리는 공지입니다. 명단은 개인정보라 앱에 싣지 않으니 원문에서 확인하세요.",
                 "_redacted": True}
        keep.append(x)
    # 같은 글이 AI대학 게시판과 SW학부 게시판에 같이 올라온 경우 → AI대학 것만 남긴다
    ai = [x for x in keep if x["source"] == "ssu_ai_college"]

    def same(a, b):
        ta, tb = re.sub(r"\W", "", a["title"]), re.sub(r"\W", "", b["title"])
        return a.get("published_at") == b.get("published_at") and difflib.SequenceMatcher(None, ta, tb).ratio() >= 0.5

    final = []
    for x in keep:
        if x["source"] == "ssu_sw_official" and any(same(x, a) for a in ai):
            skipped["중복"] += 1
            continue
        final.append(x)
    from deadline import find_deadlines, parse_deadline      # 우리 마감 규칙(server-lab/deadline.py)
    out = []
    for x in final:
        d = det.get(x["id"]) or {}
        body = (d.get("body_candidate") or "").strip() or None
        if d.get("potential_personal_data") or x.get("_redacted"):
            body = None
        pub = date.fromisoformat(x["published_at"][:10]) if x.get("published_at") else date.today()
        extra_dl = []
        t_dt, t_st = parse_deadline(x.get("title") or "", pub)
        if t_dt:
            extra_dl.append(dl("APPLICATION", "마감", at_s=t_dt.strftime("%Y-%m-%dT%H:%M")) if t_st == "FOUND"
                            else dl("APPLICATION", "마감", t_dt.strftime("%Y-%m-%d")))
        elif body and re.search(r"모집|신청|지원|접수|제출", x.get("title") or ""):   # 안내글 본문의 행사 시각을 마감으로 착각하지 않게
            b_dt, b_st, _ = find_deadlines(body, pub)
            if b_dt:
                extra_dl.append(dl("APPLICATION", "마감", at_s=b_dt.strftime("%Y-%m-%dT%H:%M")))
        y = convert(x, body=body, deadlines_extra=extra_dl)
        if x.get("_redacted"):
            y.update(content_kind="본문없음", attachments=[])
        out.append(y)
    print(f"  학교: {len(rows)}건 중 {len(out)}건 반영 (제외: {skipped})")
    return out


def generic():
    known = {"lh_myhome_snapshot.json", "lh_live.json", "myhome_live.json", "ontongyouth_live.json", "ontongyouth.json", "mongddang_list.json", "mongddang_detail.json",
             "school_notices.json", "ai_college_detail.json"}
    out = []
    for p in sorted(glob.glob(os.path.join(DATA, "*.json"))):
        if os.path.basename(p) in known:
            continue
        rows = notices_of(json.load(open(p, encoding="utf-8")))
        if rows and isinstance(rows[0], dict) and "source" in rows[0] and "title" in rows[0]:
            out += [convert(x) for x in rows]
            print(f"  기타 파일 {os.path.basename(p)}: {len(rows)}건")
    return out


def main():
    if not os.path.isdir(DATA):
        print("평강 데이터 폴더 없음(external/pg_data) → 건너뜀")
        return 0
    if os.getcwd() not in sys.path:
        sys.path.insert(0, os.getcwd())
    items = lh_myhome() + ontong() + mongddang() + school() + generic()
    skip = EXCLUDE_SOURCES | crawled_sources()
    if crawled_sources():
        print(f"  우리 크롤러가 받는 출처는 평강 파일 제외: {sorted(crawled_sources())}")
    items = [x for x in items if x["source"] not in skip and x["url"] and x["title"]]
    by_src: dict[str, list] = {}
    seen = set()
    for x in items:
        if x["id"] in seen:
            continue
        seen.add(x["id"])
        by_src.setdefault(x["source"], []).append(x)
    os.makedirs(INBOX, exist_ok=True)
    for old in glob.glob(os.path.join(INBOX, "pg_*.json")):     # 출처가 빠졌으면 예전 파일도 지운다
        if os.path.basename(old)[3:-5] not in by_src:
            os.remove(old)
    for src, rows in sorted(by_src.items()):
        path = os.path.join(INBOX, f"pg_{src}.json")
        new = json.dumps(rows, ensure_ascii=False, indent=1)
        if not os.path.exists(path) or open(path, encoding="utf-8").read() != new:   # 바뀐 때만 덮어씀
            open(path, "w", encoding="utf-8").write(new)
        print(f"  {src}: {len(rows)}건 → {path}")
    print(f"평강 데이터 변환 완료: 총 {sum(map(len, by_src.values()))}건")
    return 0


if __name__ == "__main__":
    sys.exit(main())
