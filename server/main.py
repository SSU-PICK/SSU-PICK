"""SSU:Pick 서버: 정해진 시각에 공지를 수집해 DB에 저장하고, 앱에는 FastAPI로 팀 공통 JSON을 전달한다.

실행(이 폴더에서):  uvicorn main:app --host 0.0.0.0 --port 8000
환경변수(모두 선택): ADMIN_KEY, GEMINI_API_KEY, GEMINI_MODEL, COLLECT_CATCH/PAGES/DETAIL(기본 10/1/10),
  COLLECT_HOURS(기본 하루 6번), COLLECT_EVERY_MINUTES(시험용), COLLECT_ON_EMPTY(기본 1), COLLECT_AI(0이면 AI 건너뜀)
확인:  http://서버주소:8000/docs   (API를 눌러 보며 시험하는 화면)

주의: uvicorn 은 worker 1개로 실행한다. (--workers 를 쓰면 스케줄러가 여러 번 돈다)"""
from __future__ import annotations

import os
import threading
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path

from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import BackgroundTasks, FastAPI, Header, HTTPException, Query

BASE = Path(__file__).resolve().parent
os.chdir(BASE)                                   # 수집 코드가 상대 경로(hyetaek.db, *.json)를 쓰므로 이 폴더에서 돌게 한다

import collect_all                               # noqa: E402  (수집 → AI → DB → 공통 JSON)
from export_common import to_common              # noqa: E402  (DB 한 건 → 팀 공통 JSON 한 건)
from hyetaek import store                        # noqa: E402

DB = str(BASE / "hyetaek.db")
ADMIN_KEY = os.environ.get("ADMIN_KEY", "")      # 수동 수집용 비밀번호. 서버 환경변수에만 둔다
HOURS = os.environ.get("COLLECT_HOURS", "8,11,14,17,20,23")   # 수집 시각(한국시간). 기본: 하루 6번
EVERY_MIN = os.environ.get("COLLECT_EVERY_MINUTES", "")        # 시험용: 값이 있으면 시각 대신 N분마다 수집
ON_EMPTY = os.environ.get("COLLECT_ON_EMPTY", "1") == "1"      # DB가 비어 있으면 서버를 켤 때 첫 수집을 바로 돌림
PARAMS = dict(catch=int(os.environ.get("COLLECT_CATCH", "10")),     # 슈캐치: 카테고리당 받을 공지 수
              pages=int(os.environ.get("COLLECT_PAGES", "1")),      # 잡슈: 목록 페이지 수
              detail=int(os.environ.get("COLLECT_DETAIL", "10")))   # 잡슈: 목록마다 상세를 받을 개수
if os.environ.get("COLLECT_AI") == "0":                         # 0 이면 AI 단계 건너뜀(빠른 시험용)
    PARAMS["ai"] = False

_lock = threading.Lock()                         # 수집이 겹쳐 돌지 않게
_index: dict[str, tuple[int, dict]] = {}         # 공통 id → (version, 공통 JSON)  (본문 포함)
_state = {"last_run": None, "last_finished": None, "last_ok": None, "running": False,
          "last_new_or_changed": None}   # 마지막 수집에서 새로 생기거나 바뀐 공지 수 (0이면 전부 중복으로 건너뜀)


def rebuild_index() -> int:
    """DB를 읽어 메모리에 공통 JSON 목록을 만든다. 수집이 끝날 때마다 다시 만든다."""
    con = store.connect(DB)
    try:
        items = store.feed_since(con, 0, 10**6)["items"]
        body = dict(con.execute("SELECT id, body_text FROM notices").fetchall())
    finally:
        con.close()
    new = {}
    for n in items:
        n["body_text"] = body.get(n["id"])
        rec = to_common(n)
        new[rec["id"]] = (n["version"], rec)
    _index.clear()
    _index.update(new)
    return len(new)


def collect_job() -> None:
    """수집 → AI → DB → 공통 JSON 을 한 번 돌리고 목록을 새로 만든다."""
    if not _lock.acquire(blocking=False):        # 이미 수집 중이면 건너뜀
        return
    try:
        _state.update(running=True, last_run=datetime.now().isoformat(timespec="seconds"))
        before = max((v for v, _ in _index.values()), default=0)
        res = collect_all.run_once(**PARAMS)
        _state["last_ok"] = all(res.values())
        rebuild_index()
        _state["last_new_or_changed"] = sum(1 for v, _ in _index.values() if v > before)
    except Exception as e:                       # 한 번 실패해도 서버는 계속 돌아야 한다
        _state["last_ok"] = False
        print("수집 실패:", e, flush=True)
    finally:
        _state.update(running=False, last_finished=datetime.now().isoformat(timespec="seconds"))
        _lock.release()


@asynccontextmanager
async def lifespan(app: FastAPI):
    print(f"공지 {rebuild_index()}건 불러옴", flush=True)
    sch = BackgroundScheduler(timezone="Asia/Seoul")
    if EVERY_MIN:
        sch.add_job(collect_job, "interval", minutes=int(EVERY_MIN), max_instances=1, coalesce=True)
    else:
        sch.add_job(collect_job, "cron", hour=HOURS, minute=0, max_instances=1, coalesce=True, misfire_grace_time=600)
    sch.start()
    if ON_EMPTY and not _index:                  # 새 서버(빈 DB)면 다음 수집 시각까지 기다리지 않고 바로 첫 수집
        print("DB가 비어 있어 첫 수집을 시작합니다", flush=True)
        threading.Thread(target=collect_job, daemon=True).start()
    yield
    sch.shutdown(wait=False)


app = FastAPI(title="SSU:Pick API", version="1.0", lifespan=lifespan)


@app.get("/v1/status")
def status():
    """서버가 살아 있는지, 마지막 수집이 언제인지, 최신 버전이 몇인지."""
    latest = max((v for v, _ in _index.values()), default=0)
    return {"notices": len(_index), "latest_version": latest, **_state}


@app.get("/v1/notices")
def list_notices(since: int = Query(0, ge=0, description="앱이 가진 마지막 버전. 그 이후 새로 생기거나 바뀐 공지만 받는다"),
                 limit: int = Query(200, ge=1, le=500),
                 origin: str | None = Query(None, pattern="^(CAMPUS|EXTERNAL)$", description="선택: CAMPUS(교내)만 / EXTERNAL(교외)만. 비우면 전부")):
    """바뀐 공지 목록(본문 제외). 응답의 next_version 을 앱이 저장해 두었다가 다음 요청의 since 로 쓴다.
    교내·교외는 DB를 나누지 않고 공지마다 붙은 origin 칸으로 구분한다 → 버전 번호는 하나라 최신화 방식은 그대로."""
    rows = sorted(((v, r) for v, r in _index.values() if v > since and (origin is None or r.get("origin") == origin)),
                  key=lambda x: x[0])[:limit]
    items = [{k: val for k, val in r.items() if k != "body"} for _, r in rows]
    return {"items": items, "next_version": rows[-1][0] if rows else since}


@app.get("/v1/notices/{nid:path}")
def notice_detail(nid: str):
    """공지 한 건의 상세(본문 포함). id 예: ssu_catch:28ef28380247"""
    hit = _index.get(nid)
    if not hit:
        raise HTTPException(404, "공지를 찾을 수 없습니다")
    return hit[1]


@app.post("/v1/collect")
def collect_now(bg: BackgroundTasks, x_admin_key: str = Header(default="")):
    """지금 바로 수집(관리자용). 헤더 X-Admin-Key 가 서버의 ADMIN_KEY 와 같아야 한다."""
    if not ADMIN_KEY or x_admin_key != ADMIN_KEY:
        raise HTTPException(403, "관리자 키가 필요합니다")
    if _state["running"]:
        return {"started": False, "reason": "이미 수집 중"}
    bg.add_task(collect_job)
    return {"started": True}
