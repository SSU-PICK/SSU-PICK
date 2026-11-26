"""STEP 3: 카테고리마다 N개씩 본문까지 받아서 '본문을 제대로 가져왔는지' 자동 점검한다.
실행: python step3_verify_body.py        (카테고리당 2개)
      python step3_verify_body.py 5      (카테고리당 5개)
"""
import json
import os
import sys
from datetime import date

import known
from deadline import find_deadlines, missing_kind, parse_deadline
from helpers import categories_from, get_html, list_url, notices_from, parse_detail

N = int(sys.argv[1]) if len(sys.argv) > 1 else 2
MAX_AGE_DAYS = int(os.environ.get("MAX_AGE_DAYS", "180"))   # 게시된 지 이보다 오래된 글(목록 상단 고정글 등)은 받지 않는다
IMG_EXT = (".jpg", ".jpeg", ".png", ".gif", ".webp")
NOISE_WORDS = ["Copyright", "개인정보처리방침", "사이트맵", "HOT 공지사항", "이전글", "다음글"]   # 메뉴/푸터/사이드바 글자가 섞였는지 검사용

KNOWN = known.load()
skipped = old = 0
cats = categories_from(get_html(list_url()))
rows, seen = [], {}
failed = 0
for cat in cats:
    try:
        items = notices_from(get_html(list_url(cat)), cat)[:N]
    except Exception as e:                          # noqa: BLE001  한 카테고리가 안 열려도 나머지는 계속
        print("  목록 실패:", cat, type(e).__name__)
        failed += 1
        continue
    for it in items:
        key = (" ".join(it["title"].split()), str(it["date"]))   # 같은 글이 여러 카테고리에 올라온 경우 합침
        if key in seen:
            seen[key]["categories"].append(cat)
            continue
        if it["date"] and (date.today() - it["date"]).days > MAX_AGE_DAYS:   # 오래된 고정글 → 상세도 안 읽는다
            old += 1
            seen[key] = {"categories": []}
            continue
        if known.is_known(KNOWN, it["url"], it["title"]):       # 이미 DB에 있는 공지 → 상세 페이지를 읽지 않는다
            skipped += 1
            seen[key] = {"categories": []}
            continue
        try:
            d = parse_detail(get_html(it["url"]), it["title"], cat)
        except Exception as e:                      # noqa: BLE001  이 공지만 건너뛴다 → DB에 없으니 다음 수집 때 다시 시도
            print("  상세 실패:", it["title"][:30], type(e).__name__)
            failed += 1
            continue
        body = d["body"]
        dl, st, cands = (None, "NOT_FOUND", [])
        if it["date"]:
            dl, st = parse_deadline(it["title"], it["date"])            # 1순위: 제목
            if not dl:
                dl, st, cands = find_deadlines(body, it["date"])        # 2순위: 본문 (신청/접수 줄만)
        docs = [x for x in d["attachments"] if not x["url"].split("?")[0].lower().endswith(IMG_EXT)]   # 그림 파일이 아닌 첨부
        kind = ("TEXT" if len(body) >= 100 else
                "이미지" if d["images"] and not docs else
                "첨부만" if d["attachments"] else
                "이미지" if d["images"] else "본문없음")
        if st == "NOT_FOUND":
            st = missing_kind(it["title"], kind)            # 마감이 없는 정보 공지인지, 확인이 필요한 건지 구분
        checks = {
            "본문있음": len(body) >= 100,
            "잡음없음": not any(w in body for w in NOISE_WORDS),
        }
        rows.append({**it, "date": str(it["date"]), "body": body, "attachments": d["attachments"], "images": d["images"],
                     "deadline": dl.isoformat(timespec="minutes") if dl else None, "deadline_status": st,
                     "checks": checks, "categories": [cat], "content_kind": kind, "deadline_candidates": cands})
        seen[key] = rows[-1]

print(f"이미 아는 공지라 건너뜀: {skipped}개 / 오래된 글(>{MAX_AGE_DAYS}일) 건너뜀: {old}개 / 새로 읽음: {len(rows)}개 / 실패(다음에 재시도): {failed}개")
print(f"{'카테고리':<8}{'본문글자':>7}  유형   잡음 첨부 마감       상태             제목")
for r in rows:
    c = r["checks"]
    print(f"{r['category']:<8}{len(r['body']):>7}  {r['content_kind']:<5} {'O' if c['잡음없음'] else 'X'}    "
          f"{len(r['attachments']):>2}  {(r['deadline'] or '-')[:10]:<10} {r['deadline_status'][:16]:<16} {r['title'][:24]}")

ok = sum(all(r["checks"].values()) for r in rows)
print(f"\n종합: {len(rows)}개 중 {ok}개 통과 ({ok/len(rows):.0%})" if rows else "수집된 공지 없음")
n = len(rows)
kinds = {k: sum(1 for r in rows if r["content_kind"] == k) for k in ("TEXT", "첨부만", "이미지", "본문없음")}
print(f"본문 유형: 텍스트 {kinds['TEXT']} / 첨부만 {kinds['첨부만']} / 이미지만 {kinds['이미지']} / 본문없음 {kinds['본문없음']}")
print("마감일 찾음:", sum(1 for r in rows if r["deadline"]), "/", n,
      " (제목", sum(1 for r in rows if r["deadline"] and not r["deadline_status"].startswith("BODY")),
      "· 본문", sum(1 for r in rows if r["deadline"] and r["deadline_status"].startswith("BODY")), ")",
      " 후보 여러 개:", sum(1 for r in rows if r["deadline_status"] == "BODY_MULTIPLE"),
      " 상시:", sum(1 for r in rows if r["deadline_status"] == "OPEN_ENDED"))
print("마감 없음(정보 공지):", sum(1 for r in rows if r["deadline_status"] == "INFO_NO_DEADLINE"),
      " / 확인 필요: 본문", sum(1 for r in rows if r["deadline_status"] == "CHECK_BODY"),
      "· 첨부", sum(1 for r in rows if r["deadline_status"] == "CHECK_ATTACHMENT"))
json.dump(rows, open("step3_notices.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2, default=str)
print("저장: step3_notices.json  (VS Code로 열어서 본문 2~3개를 브라우저와 비교해 보세요)")
