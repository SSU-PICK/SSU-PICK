"""학교 학과·학생회 + 청년몽땅 공지를 모아 site_notices.json 으로 저장한다. (평강이 Colab에서 한 번 받은 것을 서버에서 계속 받게 만든 것)
실행:  python site_collect.py                 (사이트마다 목록 2페이지, 새 글 상세 최대 20개)
       python site_collect.py --only ssu_ai_college --pages 1      (한 사이트만 시험)
대상
  ssu_ai_college   AI대학 공지            https://ai.ssu.ac.kr/board/notice
  ssu_sw_official  소프트웨어학부 공지     https://sw.ssu.ac.kr/bbs/board.php?bo_table=notice
  ssu_council      총학생회 링크트리       https://linktr.ee/focussu_66th
  ssu_aisw         AI SW 학생회 링크트리   https://linktr.ee/aisw2026
  youth_mongddang  청년몽땅정보통 청년지원정보  https://youth.seoul.go.kr/infoData/sprtInfo/list.do?key=2309130006
결과 모양은 job_notices.json 과 같다(+ source, sid) → AI 추출(step4) → DB(build_db) 를 그대로 탄다.
사이트 하나가 실패해도 나머지는 계속한다. 각 사이트 첫 HTML 은 raw_site_<출처>_*.html 로 저장(구조 확인용)."""
from __future__ import annotations

import argparse
import hashlib
import html as html_lib
import json
import re
import time
from datetime import date, timedelta
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from deadline import find_deadlines, parse_deadline

UA = {"User-Agent": "SSU-Hyetaek-Student-Project (mobile programming class)"}
MAX_AGE_DAYS = 180
FILE_EXT = (".pdf", ".hwp", ".hwpx", ".xls", ".xlsx", ".doc", ".docx", ".ppt", ".pptx", ".zip")
D4 = re.compile(r"(20\d{2})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})")
D2 = re.compile(r"(?<!\d)(\d{2})-(\d{2})-(\d{2})(?!\d)")          # 그누보드 목록: 26-11-17
TODAY = date.today()
PRIVACY = re.compile(r"선발자|선발\s*결과|합격자|당첨자|명단|최종\s*선발")   # 이름·학번이 실릴 수 있는 글
PRIVACY_SUMMARY = "선발·합격 결과를 알리는 공지입니다. 명단은 개인정보라 앱에 싣지 않으니 원문에서 확인하세요."


def redact(x):
    """명단 글: 제목·원문 링크·안내 문장만 남기고 본문·첨부·그림은 버린다 (AI도 읽지 않음)"""
    x.update(body="", attachments=[], images=[], content_kind="본문없음", summary=PRIVACY_SUMMARY, privacy_redacted=True,
             deadline=None, deadline_status="INFO_NO_DEADLINE", deadline_candidates=[])
    return x


def get(url):
    for attempt in range(3):
        time.sleep(1)                                    # 사이트 부담을 줄이려고 1초 간격
        try:
            r = requests.get(url, headers=UA, timeout=(10, 30))
            r.raise_for_status()
            r.encoding = r.apparent_encoding if r.encoding in (None, "ISO-8859-1") else r.encoding
            return r.text
        except (requests.ConnectionError, requests.Timeout):
            if attempt == 2:
                raise
            time.sleep(3 * (attempt + 1))


def find_date(text):
    m = D4.search(text or "")
    if m:
        y, mo, d = map(int, m.groups())
    else:
        m = D2.search(text or "")
        if not m:
            return None
        y, mo, d = 2000 + int(m.group(1)), int(m.group(2)), int(m.group(3))
    try:
        return date(y, mo, d).isoformat()
    except ValueError:
        return None


def clean(t):
    return re.sub(r"\s+", " ", t or "").strip()


def text_of(node):
    if node is None:
        return ""
    for x in node.select("script, style"):
        x.decompose()
    lines = [clean(s) for s in node.get_text("\n").split("\n")]
    return "\n".join(s for s in lines if s)


def body_node(soup, selectors):
    """선택자 순서대로 보되, 같은 선택자에 맞는 칸이 여러 개면 글이 가장 긴 칸을 고른다 (청년몽땅은 .cont 가 여러 개)"""
    for s in selectors:
        nodes = [n for n in soup.select(s) if len(clean(n.get_text())) >= 20]
        if nodes:
            return max(nodes, key=lambda n: len(clean(n.get_text())))
    return None


def files_and_images(node, base):
    atts, imgs = [], []
    if node is None:
        return atts, imgs
    for a in node.find_all("a", href=True):
        href = urljoin(base, a["href"])
        if "download" in href.lower() or href.lower().split("?")[0].endswith(FILE_EXT):
            if href not in [x["url"] for x in atts]:
                atts.append({"name": clean(a.get_text()) or None, "url": href})
    for im in node.find_all("img", src=True):
        src = urljoin(base, im["src"])
        if not src.startswith("data:") and src not in imgs:
            imgs.append(src)
    return atts, imgs


def notice(source, sid, url, title, posted, body, atts, imgs, list_name):
    pub = date.fromisoformat(posted) if posted else TODAY
    dl, st = parse_deadline(title, pub)
    cands = []
    if not dl and body:
        dl, st, cands = find_deadlines(body, pub)
    kind = "TEXT" if len(body) >= 100 else ("이미지" if imgs else ("첨부만" if atts else ("TEXT" if body else "본문없음")))
    return {"source": source, "sid": sid, "list": list_name, "title": title, "url": url, "posted": posted, "date": posted,
            "deadline": dl.strftime("%Y-%m-%dT%H:%M") if dl else None, "deadline_status": st,
            "deadline_candidates": [str(c) for c in cands][:5] if cands else [],
            "body": body, "attachments": atts, "images": imgs, "content_kind": kind}


def too_old(posted):
    return bool(posted) and posted < (TODAY - timedelta(days=MAX_AGE_DAYS)).isoformat()


def rows_by_link(html, base, pattern):
    """목록에서 상세 링크(정규식 pattern)를 찾고, 같은 줄(tr/li)의 글자에서 날짜를 찾는다."""
    soup = BeautifulSoup(html, "lxml")
    out, seen = [], set()
    for a in soup.find_all("a", href=True):
        m = re.search(pattern, a["href"]) or re.search(pattern, a.get("onclick") or "")   # 청년몽땅은 onclick="goView('75945')"
        if not m or m.group(1) in seen:
            continue
        title = clean(a.get("title") or a.get_text())
        if len(title) < 2:
            continue
        seen.add(m.group(1))
        row = a.find_parent(["tr", "li"]) or a.parent
        out.append({"sid": m.group(1), "url": urljoin(base, a["href"]), "title": title,
                    "posted": find_date(row.get_text(" ") if row else "")})
    return out


# ───── 사이트별 ─────
def board(source, list_urls, pattern, detail_sel, list_name, pages, limit, KNOWN):
    import known
    rows = []
    for p in range(1, pages + 1):
        url = list_urls(p)
        html = get(url)
        if p == 1:
            open(f"raw_site_{source}_list.html", "w", encoding="utf-8").write(html)
        rows += [r for r in rows_by_link(html, url, pattern) if r["sid"] not in {x["sid"] for x in rows}]
    print(f"\n=== {list_name}: 목록 {len(rows)}개")
    out, n = [], 0
    for r in rows:
        if too_old(r["posted"]) or known.is_known(KNOWN, r["url"], r["title"]):
            continue
        if PRIVACY.search(r["title"]):                           # 명단 글은 상세를 읽지 않고 제목·링크만
            out.append(redact(notice(source, r["sid"], r["url"], r["title"], r["posted"], "", [], [], list_name)))
            print(f"  (명단 글: 제목·링크만) {r['title'][:40]}")
            continue
        if n >= limit:
            break
        n += 1
        try:
            html = get(r["url"])
        except Exception as e:                                   # noqa: BLE001
            print("  상세 실패:", r["title"][:30], type(e).__name__)
            continue
        if n == 1:
            open(f"raw_site_{source}_detail.html", "w", encoding="utf-8").write(html)
        soup = BeautifulSoup(html, "lxml")
        node = body_node(soup, detail_sel)
        body = text_of(node)[:20000]
        posted = r["posted"] or find_date(clean((soup.select_one(".if_date, .date, .info, .view-info, .board-view-info") or soup).get_text(" "))[:3000])
        if too_old(posted):
            continue
        atts, imgs = files_and_images(soup.select_one("#bo_v_file, .bo_v_file, .file, .attach, .board-file") or node, r["url"])
        if not atts:                                             # 첨부 칸 위치를 모르면 페이지 전체에서 '다운로드/문서 파일' 링크만
            atts = files_and_images(soup, r["url"])[0][:10]
        if node is not None:
            a2, i2 = files_and_images(node, r["url"])
            atts += [x for x in a2 if x["url"] not in [y["url"] for y in atts]]
            imgs += [x for x in i2 if x not in imgs]
        x = notice(source, r["sid"], r["url"], r["title"], posted, body, atts, imgs, list_name)
        out.append(x)
        print(f"  {x['deadline'] or '-':<17} {x['content_kind']:<4} 본문 {len(body):>5}자 첨부 {len(atts)} 그림 {len(imgs)} | {posted} {r['title'][:34]}")
    return out


LINK_SKIP = re.compile(r"카카오|채널|홈페이지|건의|게시판|규정|세칙|회칙|규칙|예약|소통\s*창구|공지사항\s*\||인스타|instagram|유튜브|youtube|"
                       r"가이드북|플러스\s*친구|SSUport|통합\s*시스템|오픈채팅", re.I)


def linktree(source, url, list_name):
    html = get(url)
    open(f"raw_site_{source}_list.html", "w", encoding="utf-8").write(html)
    soup = BeautifulSoup(html, "lxml")
    links = []
    tag = soup.find("script", id="__NEXT_DATA__")
    if tag and tag.string:
        def walk(o):
            if isinstance(o, dict):
                if isinstance(o.get("url"), str) and isinstance(o.get("title"), str) and o["url"].startswith("http"):
                    links.append((clean(o["title"]), o["url"]))
                for v in o.values():
                    walk(v)
            elif isinstance(o, list):
                for v in o:
                    walk(v)
        walk(json.loads(tag.string))
    if not links:                                                 # 구조가 바뀌었으면 화면의 링크로
        links = [(clean(a.get_text()), a["href"]) for a in soup.find_all("a", href=True) if a["href"].startswith("http")]
    out, seen = [], set()
    for title, u in links:
        if not title or u in seen or "linktr.ee" in u or LINK_SKIP.search(title):
            continue
        seen.add(u)
        sid = hashlib.sha256(u.encode()).hexdigest()[:20]          # 평강 데이터와 같은 번호 규칙
        x = notice(source, sid, u, title, None, "", [], [], list_name)
        out.append(redact(x) if PRIVACY.search(title) else x)
    print(f"\n=== {list_name}: 링크 {len(links)}개 중 공지로 볼 것 {len(out)}개")
    for x in out:
        print(f"  {x['deadline'] or '-':<17} | {x['title'][:40]}")
    return out


MD_LIST = "https://youth.seoul.go.kr/infoData/sprtInfo/list.do?key=2309130006&pageIndex={p}&recordCountPerPage=8"
MD_VIEW = "https://youth.seoul.go.kr/infoData/sprtInfo/view.do?key=2309130006&sprtInfoId={sid}"
MD_PERIOD = re.compile(r"신청\s*기간\s*:?\s*(20\d{2}[-.]\s*\d{1,2}[-.]\s*\d{1,2})\.?\s*(?:\([^)]*\))?\s*~\s*(20\d{2}[-.]\s*\d{1,2}[-.]\s*\d{1,2})?\.?\s*(?:\([^)]*\))?\s*(\d{1,2}\s*:\s*\d{2})?")


def mongddang_title(raw):
    raw = html_lib.unescape(raw)                                  # 목록 글자가 '&lt;제목&gt;' 처럼 한 번 더 감싸져 있음
    raw = re.sub(r"\s*\.{3,}\s*\d+\s*$", "", clean(raw))
    m = re.search(r"<\s*([^<>]{2,})\s*>", raw)
    return clean(m.group(1)) if m else raw


def mongddang(pages, limit, KNOWN):
    import known
    rows = []
    for p in range(1, pages + 1):
        html = get(MD_LIST.format(p=p))
        if p == 1:
            open("raw_site_youth_mongddang_list.html", "w", encoding="utf-8").write(html)
        for r in rows_by_link(html, MD_LIST.format(p=p), r"(?:sprtInfoId=|goView\(')(\d+)"):
            if r["sid"] not in {x["sid"] for x in rows}:
                rows.append(r)
    print(f"\n=== 청년몽땅 청년지원정보: 목록 {len(rows)}개")
    out, n = [], 0
    for r in rows:
        url = MD_VIEW.format(sid=r["sid"])
        title = mongddang_title(r["title"])
        if "게시요청 가이드" in title or known.is_known(KNOWN, url, title):
            continue
        if n >= limit:
            break
        n += 1
        try:
            html = get(url)
        except Exception as e:                                   # noqa: BLE001
            print("  상세 실패:", title[:30], type(e).__name__)
            continue
        if n == 1:
            open("raw_site_youth_mongddang_detail.html", "w", encoding="utf-8").write(html)
        soup = BeautifulSoup(html, "lxml")
        node = body_node(soup, [".cont", ".view-cont", ".board-view", "#content"])
        body = text_of(node)
        i = body.find("상세정보")
        body = body[i + 4:].strip() if i >= 0 else body
        body = re.sub(r"\n?목록\s*$", "", body)[:20000]
        whole = clean(soup.get_text(" "))
        x = notice("youth_mongddang", r["sid"], url, title, r["posted"], body, *files_and_images(node, url), "청년지원정보")
        m = MD_PERIOD.search(whole)
        if m and m.group(2):                                      # 사이트에 적힌 신청기간이 있으면 그 마감을 쓴다
            end = re.sub(r"[.\s]", "-", m.group(2)).replace("--", "-").strip("-")
            y, mo, d = [int(v) for v in re.findall(r"\d+", end)[:3]]
            hm = (m.group(3) or "").replace(" ", "")
            x["deadline"] = f"{y:04d}-{mo:02d}-{d:02d}T{hm if hm and hm not in ('00:00',) else '23:59'}"
            x["deadline_status"] = "FOUND" if hm and hm != "00:00" else "FOUND_DATE_ONLY"
            x["deadline_source"] = "site_period"
        out.append(x)
        print(f"  {x['deadline'] or '-':<17} {x['content_kind']:<4} 본문 {len(body):>5}자 | {title[:34]}")
    return out


SITES = {
    "ssu_ai_college": lambda a, K: board("ssu_ai_college", lambda p: f"https://ai.ssu.ac.kr/board/notice?page={p}",
                                         r"/board/notice/(\d+)(?:[?#]|$)", [".ck-content", ".board-view-content", ".view-content", "article"],
                                         "AI대학 공지", a.pages, a.detail, K),
    "ssu_sw_official": lambda a, K: board("ssu_sw_official", lambda p: f"https://sw.ssu.ac.kr/bbs/board.php?bo_table=notice&page={p}",
                                          r"bo_table=notice&(?:amp;)?wr_id=(\d+)", ["#bo_v_con", ".bo_v_con", "#bo_v_atc", "article"],
                                          "소프트웨어학부 공지", a.pages, a.detail, K),
    "ssu_council": lambda a, K: linktree("ssu_council", "https://linktr.ee/focussu_66th", "총학생회 링크트리"),
    "ssu_aisw": lambda a, K: linktree("ssu_aisw", "https://linktr.ee/aisw2026", "AI소프트웨어학부 학생회 링크트리"),
    "youth_mongddang": lambda a, K: mongddang(max(a.pages, 3), a.detail, K),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pages", type=int, default=2)
    ap.add_argument("--detail", type=int, default=20, help="사이트마다 새로 읽을 상세 최대 개수")
    ap.add_argument("--only", nargs="*", help="이 사이트만 (예: ssu_ai_college youth_mongddang)")
    a = ap.parse_args()
    import known
    K = known.load()
    try:                                                          # 예전에 모아 둔 것 중 명단 글은 본문·첨부를 지운다
        arch = json.load(open("archive_site_notices.json", encoding="utf-8"))
        todo = [r for r in arch if PRIVACY.search(r.get("title") or "") and not r.get("privacy_redacted")]
        for r in todo:
            redact(r)
        if todo:
            json.dump(arch, open("archive_site_notices.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    except (OSError, ValueError):
        pass
    out, ok = [], {}
    for name, fn in SITES.items():
        if a.only and name not in a.only:
            continue
        try:
            rows = fn(a, K)
            out += rows
            ok[name] = len(rows)
        except Exception as e:                                   # noqa: BLE001  사이트 하나가 실패해도 나머지는 계속
            ok[name] = f"실패 {type(e).__name__}: {str(e)[:80]}"
    json.dump(out, open("site_notices.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print("\n=== 사이트별 결과 ===")
    for k, v in ok.items():
        print(f"  {k:<16} {v}")
    print(f"저장: site_notices.json ({len(out)}개, 이미 아는 공지는 상세를 다시 읽지 않음)")
    return 0 if any(isinstance(v, int) for v in ok.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
