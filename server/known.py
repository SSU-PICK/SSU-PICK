"""이미 DB에 있는 공지는 상세 페이지를 다시 읽지 않기 위한 도우미 (목록 단계 중복 판정).
규칙: 주소(url)가 DB에 있고 제목도 같으면 '이미 아는 공지' → 상세 페이지를 받지 않는다.
      제목이 바뀌었거나 DB에 없으면 새로 읽는다. DB 파일이 없으면(첫 실행) 전부 읽는다.
예외: 환경변수 REFRESH_DAYS=N 이면, 게시일이 최근 N일 안인 공지는 다시 읽는다(수정·마감 연장 반영). 기본 0 = 다시 읽지 않음."""
import os
import sqlite3
from datetime import date, timedelta

DB = os.environ.get("HYETAEK_DB", "hyetaek.db")
REFRESH_DAYS = int(os.environ.get("REFRESH_DAYS", "0"))
FORCE = os.environ.get("FORCE_FULL") == "1"      # 1이면 중복 판정 없이 전부 다시 읽는다


def _norm(s):
    return " ".join((s or "").split())


def load():
    if FORCE or not os.path.exists(DB):
        return {}
    try:
        con = sqlite3.connect(DB)
        rows = con.execute("SELECT url, title, posted_at FROM notices").fetchall()
        con.close()
    except sqlite3.Error:
        return {}
    return {u: (_norm(t), (p or "")[:10]) for u, t, p in rows}


def is_known(known, url, title):
    hit = known.get(url)
    if not hit or hit[0] != _norm(title):
        return False
    if REFRESH_DAYS > 0 and hit[1]:
        try:
            if date.fromisoformat(hit[1]) >= date.today() - timedelta(days=REFRESH_DAYS):
                return False
        except ValueError:
            pass
    return True
