"""4단계: AI로 '규칙이 못 하는 것'만 처리한다. (마감 확정 / 신청 조건 / 한 줄 요약)

사용법 (PowerShell)
  python step4_extract.py --list-models          # 내 키로 쓸 수 있는 모델 이름 확인
  python step4_extract.py --dry-run              # AI 호출 없이 대상과 프롬프트만 확인
  python step4_extract.py --limit 3              # 3개만 먼저 시험
  python step4_extract.py                        # AI가 필요한 공지 전부 (기본 12개 안팎)
  python step4_extract.py --all                  # 텍스트 공지 전부 (요약/조건까지)
  python step4_extract.py --images               # 그림·PDF 첨부도 AI가 직접 읽기 (이미지 공지 / 첨부만 공지 / 글로 마감을 못 찾은 공지)

키: 환경변수 GEMINI_API_KEY (코드/JSON에 절대 넣지 않는다)
모델: --model 또는 환경변수 GEMINI_MODEL (모델 이름은 --list-models 결과에서 고른다)
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
import time

import requests

API = "https://generativelanguage.googleapis.com/v1beta"
NEED_AI = {"CHECK_BODY", "BODY_MULTIPLE"}
PROMPT_VERSION = "v2"      # 안내문을 바꾸면 올린다 → 이전 버전으로 처리한 공지는 다음 실행 때 자동으로 다시 처리
MEDIA_VERSION = "m1"       # 그림·PDF·HWP 를 같이 읽는 공지용 안내문 버전 (글만 있는 공지는 PROMPT_VERSION 그대로 → 다시 안 부른다)

PROMPT = """너는 대학 공지에서 정보를 뽑는 도구다. 아래 공지 '본문'에 적힌 내용만 사용한다. 추측 금지.
JSON 하나만 출력한다. 모르면 null. 키는 정확히 다음과 같다.

{{
 "deadline": "신청/접수/지원/제출 마감 일시 'YYYY-MM-DD HH:MM' (시간 모르면 23:59). 없거나 불확실하면 null",
 "deadline_evidence": "마감 근거가 되는 본문 문장을 그대로 복사 (null이면 null)",
 "deadlines": [ {{"label": "프로그램/구분 이름", "datetime": "YYYY-MM-DD HH:MM", "evidence": "본문 문장 그대로"}} ],
 "events": [ {{"label": "행사/훈련/설명회/시험 이름", "datetime": "YYYY-MM-DD HH:MM", "evidence": "본문 문장 그대로"}} ],
 "gender": "MALE_ONLY 또는 FEMALE_ONLY 또는 null",
 "gender_evidence": "성별 제한의 근거 문장을 그대로 복사 (null이면 null)",
 "summary": "공지를 60자 이내 한 문장으로 요약",
 "conditions": [
   {{"type": "grade|department|status|nationality|gpa|other",
     "value": "예: 3학년 / 컴퓨터학부 / 재학생 / 외국인 / 평점 3.0 이상",
     "evidence": "근거가 되는 본문 문장을 그대로 복사"}}
 ]
}}

규칙
- 여러 일정이 있으면 '신청 마감'만 deadline에 넣는다. 면접/발표/운영/임용 기간은 아니다.
- 프로그램/구분별로 마감이 여러 개이면(표 등) deadline은 null로 두고 deadlines에 모두 넣는다. 마감이 하나면 deadlines는 [].
- 마감이 '조기/일반'처럼 둘이면 일반(최종) 마감을 deadline에, 둘 다 deadlines에 넣는다.
- events는 '열리는 날'이다(훈련, 설명회, 시험, 행사 일시). 신청 마감이 아니다. 없으면 [].
- gender는 본문이 남학생만/여학생만 대상이라고 분명히 쓴 경우만 쓴다(예: 예비군 훈련 = MALE_ONLY). 모르면 null.
- 재학 상태 조건은 value에 '재학생', '휴학생', '졸업예정자', '졸업생' 중 해당하는 말을 그대로 쓴다. 본문이 '휴학·졸업·수료자는 제외'처럼 제외 대상을 적었으면, 그 말들은 쓰지 말고 남는 '재학생'을 value로 쓴다.
- conditions는 '지원 자격 제한'만이다: 학년/학과/재학상태/국적/성적/나이 등. 아래는 조건이 아니다.
  · "외국인의 경우 여권 제출" 같은 해당자만 내는 제출서류 · "숭실대 학생분들 참여 바랍니다" 같은 인사말 · 프로그램 소개 문장
- 조건이 없으면 [].
- evidence는 본문에 있는 문장을 한 글자도 바꾸지 말고 복사한다.

[제목] {title}
[게시일] {date}
[본문]
{body}
"""

IMG_PROMPT = PROMPT.replace(
    "아래 공지 '본문'에 적힌 내용만 사용한다.",
    "함께 보낸 공지 그림(포스터)·PDF 첨부와 아래 '본문'(첨부 문서에서 뽑은 글 포함)에 적힌 내용만 사용한다. "
    "그림·PDF에서 읽은 내용도 같은 규칙으로 뽑고, evidence에는 그림·PDF·문서에 적힌 문장을 그대로 옮겨 적는다."
).replace(
    '"summary": "공지를 60자 이내 한 문장으로 요약",',
    '"summary": "60자 이내 한 문장. 제목을 그대로 반복하지 말고 대상·기간·방법 중 핵심을 담는다",\n'
    ' "key_points": ["그림·PDF·첨부 문서에서 읽은 핵심 내용을 3~6줄로 (대상, 신청 기간, 방법, 장소, 문의 등). 한 줄 80자 이내"],'
)

# 그림·PDF 는 Gemini 가 직접 읽는다. HWP/HWPX 는 글자를 뽑아서(hwp_text.py) 본문에 붙여 보낸다.
# 엑셀/워드/ppt/zip 은 받지 않는다.
SKIP_EXT = (".zip", ".xls", ".xlsx", ".doc", ".docx", ".ppt", ".pptx")
DOC_LIMIT = 8000            # 첨부 문서 하나에서 보낼 최대 글자 수
MAX_FILE = 15_000_000       # 파일 하나 최대 15MB
MAX_TOTAL = 18_000_000      # 한 번에 보내는 합계 (Gemini 요청 한도 20MB 아래로)


def norm(s: str) -> str:
    return re.sub(r"\s+", "", s or "")


COND_TYPES = {"grade", "department", "status", "nationality", "gpa", "age", "other"}
_GREETING = {"숭실대학교", "숭실대", "숭실대학교학생", "숭실대학교학생분들", "숭실대학생", "본교학생", "본교"}


def _days_before(dt: str, posted: str | None):
    from datetime import date
    try:
        return (date.fromisoformat(posted[:10]) - date.fromisoformat(str(dt)[:10])).days
    except (TypeError, ValueError):
        return None


def _year_typo(res: dict, posted: str | None) -> bool:
    """그림 속 연도 오타(예: 2026학년도 공지인데 '2025.11.23.')가 있는 답인가:
    날짜 중 하나라도 게시일보다 '거의 정확히 1년'(300~430일) 앞이면 그 공지 전체가 연도를 잘못 적은 것으로 본다."""
    dates = [res.get("deadline")] + [d.get("datetime") for k in ("deadlines", "events") for d in res.get(k) or [] if isinstance(d, dict)]
    return any(300 <= (_days_before(x, posted) or 0) <= 430 for x in dates if x)


def _fix_year(dt: str, posted: str | None, typo: bool):
    """연도 오타가 있는 답이면, 게시일보다 60일 넘게 앞선 날짜에 1년을 더한다.
    오타가 없는 답(예: 이미 끝난 4월 1차 모집)은 그대로 둔다."""
    if not typo or (_days_before(dt, posted) or 0) <= 60:
        return dt
    try:
        y = int(dt[:4]) + 1
        return f"{y}{dt[4:]}" if dt[5:10] != "02-29" else f"{y}-02-28{dt[10:]}"
    except ValueError:
        return dt


def validate(res: dict, body: str, from_image: bool = False, posted: str | None = None) -> dict:
    """AI 답을 믿기 전에 검사한다. 근거 문장이 본문(첨부 문서 글 포함)에 없으면 그 값은 버린다.
    그림·PDF 는 글자로 대조할 수 없어서 받아 두되 '확인 필요'로 표시한다(그림에서만 나온 값이 있을 때만)."""
    out = {"deadline": None, "deadline_evidence": None, "deadline_verified": False, "deadlines": [], "events": [], "gender": None,
           "summary": None, "key_points": [], "conditions": [], "dropped": [], "fixed_year": [], "needs_review": False}
    nb = norm(body)
    typo = _year_typo(res, posted)

    def check(ev):
        """(받아들임, 본문으로 확인됨)"""
        verified = bool(ev) and norm(ev) in nb
        if verified:
            return True, True
        if from_image and ev:
            out["needs_review"] = True          # 그림·PDF 에서만 나온 값 → 사람이 원문 확인
            return True, False
        return False, False

    def date_ok(x):
        x = str(x or "").strip()
        if not re.fullmatch(r"20\d{2}-\d{2}-\d{2}( \d{2}:\d{2})?", x):
            return None
        y = _fix_year(x, posted, typo)
        if y and y != x:
            out["fixed_year"].append(f"{x}->{y}")
        return y

    dl = date_ok(res.get("deadline"))
    if res.get("deadline"):
        acc, ver = check(res.get("deadline_evidence"))
        if dl and acc:
            out.update(deadline=dl, deadline_evidence=res.get("deadline_evidence"), deadline_verified=ver)
        else:
            out["dropped"].append(f"deadline:{res.get('deadline')}")
    for key in ("deadlines", "events"):
        for d in res.get(key) or []:
            x = date_ok(d.get("datetime")) if isinstance(d, dict) else None
            if x and check(d.get("evidence"))[0]:
                out[key].append({"label": d.get("label"), "datetime": x, "evidence": d.get("evidence")})
            else:
                out["dropped"].append(f"{key}:{(d or {}).get('datetime') if isinstance(d, dict) else d}")
    if res.get("gender") in ("MALE_ONLY", "FEMALE_ONLY"):
        if check(res.get("gender_evidence"))[0]:
            out["gender"] = res["gender"]
        else:
            out["dropped"].append(f"gender:{res['gender']}")
    s = res.get("summary")
    if isinstance(s, str) and s.strip():
        out["summary"] = s.strip()[:80]
    kp = res.get("key_points")
    if isinstance(kp, list):
        out["key_points"] = [str(k).strip()[:120] for k in kp if isinstance(k, str) and k.strip()][:6]
    for c in res.get("conditions") or []:
        if isinstance(c, dict) and c.get("value") and norm(str(c["value"])) not in _GREETING and check(c.get("evidence"))[0]:
            t = c.get("type") if c.get("type") in COND_TYPES else "other"
            out["conditions"].append({"type": t, "value": c["value"], "evidence": c.get("evidence")})
        else:
            out["dropped"].append(f"condition:{(c or {}).get('value') if isinstance(c, dict) else c}")
    return out


def call_gemini(key: str, model: str, prompt: str, images: list[tuple[str, bytes]] | None = None, tries: int = 4) -> dict:
    parts = [{"text": prompt}]
    for mime, data in images or []:
        parts.append({"inline_data": {"mime_type": mime, "data": base64.b64encode(data).decode()}})
    payload = {"contents": [{"parts": parts}],
               "generationConfig": {"responseMimeType": "application/json", "temperature": 0}}
    url = f"{API}/models/{model}:generateContent"
    for i in range(tries):
        r = requests.post(url, headers={"x-goog-api-key": key, "Content-Type": "application/json"},
                          json=payload, timeout=90)
        if r.status_code in (429, 500, 503):
            wait = 15 * (i + 1)
            print(f"   (한도/일시 오류 {r.status_code}, {wait}초 후 재시도)")
            time.sleep(wait)
            continue
        if r.status_code != 200:
            raise RuntimeError(f"HTTP {r.status_code}: {r.text[:300]}")
        data = r.json()
        text = data["candidates"][0]["content"]["parts"][0]["text"]
        return json.loads(text)
    raise RuntimeError("재시도 실패 (무료 한도일 수 있음: 잠시 뒤 다시 실행하면 이어서 합니다)")


def _download(u: str, referer: str):
    """첨부 하나를 받아서 ('media', mime, bytes) 또는 ('doc', None, 글자) 로 돌려준다. 못 읽는 형식이면 None"""
    from hwp_text import hwp_bytes_to_text, kind_of
    r = requests.get(u, timeout=60, headers={"User-Agent": "SSU-Hyetaek-Student-Project (mobile programming class)",
                                             "Referer": referer or u})
    time.sleep(1)                                       # 학교 서버 부담을 줄이려고 1초 쉰다
    if r.status_code != 200 or not r.content or len(r.content) > MAX_FILE:
        return None
    data, mime = r.content, r.headers.get("Content-Type", "").split(";")[0].strip().lower()
    if data[:4] == b"%PDF":                             # download.php 처럼 형식을 안 알려 주는 링크는 내용으로 판단
        return "media", "application/pdf", data
    if mime.startswith("image/") or data[:3] == b"\xff\xd8\xff" or data[:8] == b"\x89PNG\r\n\x1a\n":
        return "media", (mime if mime.startswith("image/") else ("image/png" if data[:4] == b"\x89PNG" else "image/jpeg")), data
    if kind_of(data):                                   # HWP / HWPX → 글자만 뽑는다
        text = hwp_bytes_to_text(data)
        return ("doc", None, text[:DOC_LIMIT]) if len(text) >= 20 else None
    return None


def fetch_media(row: dict, img_limit: int = 4, file_limit: int = 3):
    """공지의 그림·PDF(→ AI에 파일로)와 HWP(→ 글자로)를 받아 온다. 반환: (media 목록, [(첨부 이름, 글자)])"""
    media, docs, total, seen = [], [], 0, set()
    urls = list(row.get("images") or [])[:img_limit]
    names = {a["url"]: a.get("name") or "첨부" for a in row.get("attachments") or []}
    files = [u for u in names if not u.split("?")[0].lower().endswith(SKIP_EXT) and u not in urls][:file_limit]
    for u in urls + files:
        if u in seen:
            continue
        seen.add(u)
        try:
            got = _download(u, row.get("url"))
        except requests.RequestException as e:
            print("   (첨부 받기 실패)", type(e).__name__)
            continue
        if not got:
            continue
        if got[0] == "doc":
            docs.append((names.get(u, "첨부"), got[2]))
        elif total + len(got[2]) <= MAX_TOTAL:
            media.append((got[1], got[2]))
            total += len(got[2])
    return media, docs


def wants_media(r: dict) -> bool:
    """그림·첨부를 같이 읽을 공지: 내용이 그림/첨부에만 있거나, 본문이 짧고 첨부가 있거나, 글에서 마감을 못 찾았는데 그림·첨부가 있는 경우"""
    has = bool(r.get("images") or r.get("attachments"))
    if r["content_kind"] in ("이미지", "첨부만"):
        return True
    if has and len(r.get("body") or "") < 300:
        return True
    return has and r.get("deadline_status") in NEED_AI | {"CHECK_IMAGE", "CHECK_ATTACHMENT"}


def version_for(r: dict, images: bool) -> str:
    return MEDIA_VERSION if images and wants_media(r) else PROMPT_VERSION


def pick_targets(rows: list[dict], all_text: bool, images: bool) -> list[dict]:
    t = []
    for r in rows:
        if r.get("deadline_source") == "program_field" and (r.get("eligibility") or {}).get("parsed") and r["content_kind"] != "이미지":
            continue                                   # 진로취업프로그램: 마감·대상이 사이트에 구조화돼 있어 AI 불필요
        if r["content_kind"] == "TEXT" and (all_text or r.get("deadline_status") in NEED_AI):
            t.append(r)
        elif images and r["content_kind"] in ("이미지", "첨부만"):
            t.append(r)
    return t


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--list-models", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--images", action="store_true")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--model", default=os.environ.get("GEMINI_MODEL", ""))
    ap.add_argument("--delay", type=float, default=7.0, help="호출 사이 대기(초). 무료 한도 보호")
    ap.add_argument("--src", nargs="+", default=["archive_step3_notices.json", "archive_job_notices.json", "archive_program_notices.json", "archive_site_notices.json",
                                                   "step3_notices.json", "job_notices.json", "program_notices.json", "site_notices.json"], help="읽을 수집 결과 파일들(모아 둔 것 + 이번 것)")
    a = ap.parse_args()
    key = os.environ.get("GEMINI_API_KEY", "")

    if a.list_models:
        if not key:
            sys.exit("GEMINI_API_KEY 가 설정되지 않았습니다.")
        r = requests.get(f"{API}/models", headers={"x-goog-api-key": key}, params={"pageSize": 200}, timeout=30)
        for m in r.json().get("models", []):
            if "generateContent" in m.get("supportedGenerationMethods", []):
                print(m["name"].replace("models/", ""))
        return

    rows = list({x["url"]: x for f in a.src if os.path.exists(f) for x in json.load(open(f, encoding="utf-8"))}.values())   # 같은 공지는 한 번만
    targets = pick_targets(rows, a.all, a.images)
    if a.limit:
        targets = targets[:a.limit]
    chars = sum(len(r["body"]) for r in targets)
    print(f"대상 {len(targets)}개 / 본문 합계 약 {chars:,}자")

    if a.dry_run:
        for r in targets:
            print(f" - [{r.get('deadline_status', r.get('list'))}] {r['content_kind']} {len(r['body']):>5}자  {r['title'][:40]}")
        if targets:
            print("\n--- 첫 번째 프롬프트(앞 700자) ---")
            print(PROMPT.format(title=targets[0]["title"], date=targets[0].get("date") or (targets[0].get("posted") or "")[:10], body=targets[0]["body"])[:700])
        return
    if not key or not a.model:
        sys.exit("GEMINI_API_KEY 와 --model(또는 GEMINI_MODEL)이 필요합니다. 먼저 --list-models 를 실행하세요.")

    results = []
    if os.path.exists("step4_results.json"):                     # 한도에 걸려 끊겨도 이어서
        results = json.load(open("step4_results.json", encoding="utf-8"))
    have = {r["url"]: r.get("prompt_version") for r in results}      # 옛 버전 결과는 새로 처리해 덮어쓴다
    done = {t["url"] for t in targets if have.get(t["url"]) == version_for(t, a.images)}
    skipped = failed = 0
    for n, r in enumerate(targets, 1):
        if r["url"] in done:
            skipped += 1
            print(f"[{n}/{len(targets)}] (이미 처리됨) {r['title'][:40]}")
            continue
        print(f"[{n}/{len(targets)}] {r['title'][:40]}", flush=True)
        ver = version_for(r, a.images)
        imgs, docs = fetch_media(r) if ver == MEDIA_VERSION else ([], [])
        body = r["body"] or ""
        for name, text in docs:                                   # HWP 에서 뽑은 글을 본문 뒤에 붙인다 → 근거 대조도 이 글로 한다
            body += f"\n\n[첨부 문서: {name}]\n{text}"
        if imgs or docs:
            kinds = [m.split("/")[-1] for m, _ in imgs] + [f"hwp({len(t)}자)" for _, t in docs]
            print(f"   그림·PDF·문서 {len(kinds)}개 함께 읽기: {', '.join(kinds)}")
            for name, text in docs:
                print(f"     └ {name[:30]}: {text.splitlines()[0][:50] if text else ''} …")
        elif ver == MEDIA_VERSION and len(body) < 30:
            print("   읽을 수 있는 그림·PDF·문서 없음 → 건너뜀")     # 다음 실행 때 다시 받지 않도록 '처리함'으로 기록
            v = validate({}, "")
            results = [x for x in results if x["url"] != r["url"]]
            results.append({"prompt_version": ver, "final_deadline": r["deadline"], "final_source": "rule" if r["deadline"] else None,
                            "url": r["url"], "title": r["title"], "date": r.get("date") or (r.get("posted") or "")[:10], "rule_deadline": r["deadline"],
                            "rule_status": r.get("deadline_status", r.get("deadline_source")), "rule_candidates": r.get("deadline_candidates", []),
                            "media": "none", **v})
            json.dump(results, open("step4_results.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
            continue
        tpl = IMG_PROMPT if ver == MEDIA_VERSION else PROMPT
        posted = r.get("date") or (r.get("posted") or "")[:10]
        try:
            raw = call_gemini(key, a.model, tpl.format(title=r["title"], date=posted, body=body or "(본문 없음)"), imgs)
        except Exception as e:                                    # noqa: BLE001
            print("   실패:", e)
            failed += 1
            if "재시도 실패" in str(e):      # 한도 문제면 더 호출해도 소용없음
                break
            continue
        try:
            v = validate(raw, body, from_image=bool(imgs), posted=posted)
        except Exception as e:                                    # noqa: BLE001
            print("   검증 실패(AI 응답 형식 이상):", e, "| 원문:", str(raw)[:200])
            failed += 1
            continue
        rule = r["deadline"]
        print(f"   규칙: {rule or '-'} ({r.get('deadline_status', r.get('deadline_source'))})  →  AI: {v['deadline'] or '-'}  | 요약: {v['summary']}")
        if v["conditions"]:
            print("   조건:", "; ".join(f"{c['type']}={c['value']}" for c in v["conditions"]))
        if v["events"]:
            print("   행사 일정:", "; ".join(f"{d['label']}={d['datetime']}" for d in v["events"]))
        if v["gender"]:
            print("   성별 제한:", v["gender"])
        if v["deadlines"]:
            print("   마감 목록:", "; ".join(f"{d['label']}={d['datetime']}" for d in v["deadlines"]))
        if v["key_points"]:
            print("   핵심 내용:", " / ".join(k[:40] for k in v["key_points"][:3]))
        if v["fixed_year"]:
            print("   연도 바로잡음:", v["fixed_year"])
        if v["dropped"]:
            print("   버린 값(근거 없음):", v["dropped"])
        if v["needs_review"]:
            print("   ※ 그림·PDF에서만 나온 값 있음 → 확인 필요")
        final, src = (v["deadline"], "ai") if v["deadline"] and v["deadline_verified"] else (rule, "rule")
        if not final and v["deadline"]:
            final, src = v["deadline"], "ai_image"
        results = [x for x in results if x["url"] != r["url"]]
        results.append({"prompt_version": ver, "media": len(imgs), "docs": [{"name": n, "chars": len(t)} for n, t in docs], "final_deadline": final, "final_source": src if final else None, "url": r["url"], "title": r["title"], "date": r.get("date") or (r.get("posted") or "")[:10], "rule_deadline": rule,
                        "rule_status": r.get("deadline_status", r.get("deadline_source")), "rule_candidates": r.get("deadline_candidates", []), **v})
        json.dump(results, open("step4_results.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
        time.sleep(a.delay)
    have = {x["url"]: x.get("prompt_version") for x in results}
    left = [t for t in targets if have.get(t["url"]) != version_for(t, a.images)]
    print(f"\n저장: step4_results.json ({len(results)}개) | 이번에 건너뜀 {skipped} | 실패 {failed} | 대상 중 아직 없음 {len(left)}")
    for t in left:
        print("   미처리:", t["title"][:50])
    if left:
        print("→ 같은 명령을 다시 실행하면 미처리분만 이어서 합니다.")


if __name__ == "__main__":
    main()
