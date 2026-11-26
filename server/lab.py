"""서버 실습 도우미. 서버(uvicorn)를 켠 뒤 다른 터미널에서 실행한다.

  python lab.py status    서버 상태(공지 수, 마지막 수집 시각, 새로 생기거나 바뀐 공지 수)
  python lab.py collect   지금 수집을 시키고 끝날 때까지 기다린다 (실제 사이트 접속)
  python lab.py verify    앱처럼 서버에서 공지를 받아 와서 내용을 검사한다
  python lab.py doctor    (서버 켜기 전) 이 컴퓨터 준비 상태 점검: 파이썬·패키지·Gemini 키/모델·IP·포트
  python lab.py reset     (서버 끈 뒤) 모아 둔 데이터를 지우고 처음부터 다시 수집하게 만든다
환경변수: LAB_URL(기본 http://127.0.0.1:8000), ADMIN_KEY(collect 에 필요)"""
import json
import os
import sys
import time
from collections import Counter

import requests

URL = os.environ.get("LAB_URL", "http://127.0.0.1:8000").rstrip("/")


def status():
    r = requests.get(f"{URL}/v1/status", timeout=10)
    r.raise_for_status()
    return r.json()


def show(st):
    for k, v in st.items():
        print(f"  {k}: {v}")


def cmd_status():
    show(status())


def cmd_collect():
    key = os.environ.get("ADMIN_KEY", "lab-secret")   # run_lab.ps1 의 연습용 키와 같은 값
    before = status()
    print("수집 전:", before["notices"], "건 / 최신 버전", before["latest_version"])
    r = requests.post(f"{URL}/v1/collect", headers={"X-Admin-Key": key}, timeout=10)
    if r.status_code == 403:
        sys.exit("관리자 키가 틀렸습니다. 서버를 켠 창의 ADMIN_KEY 와 같은 값을 이 창에도 넣으세요.")
    print("요청 결과:", r.json())
    t0 = time.time()
    seen_running = False
    while True:                                   # 수집이 시작되어 끝날 때까지 기다린다
        time.sleep(5)
        st = status()
        seen_running = seen_running or st["running"]
        print(f"  ... {int(time.time() - t0)}초 / 수집 중={st['running']} / 현재 {st['notices']}건", flush=True)
        done = not st["running"] and (seen_running or st["last_run"] != before["last_run"])   # 이번 요청으로 한 번 돌고 끝났다
        if done or (not seen_running and time.time() - t0 > 60):
            break
    print("\n수집 후 상태")
    show(status())


def cmd_verify():
    # 1) 앱처럼 since=0 부터 받아 온다. next_version 을 저장해 items 가 빌 때까지 반복
    items, since, pages = [], 0, 0
    while True:
        r = requests.get(f"{URL}/v1/notices", params={"since": since, "limit": 200}, timeout=30)
        r.raise_for_status()
        d = r.json()
        if not d["items"]:
            break
        items += d["items"]
        since = d["next_version"]
        pages += 1
    print(f"[1] 목록 받기: {len(items)}건 ({pages}번에 나눠 받음), next_version={since}")
    if not items:
        sys.exit("받은 공지가 없습니다. 아직 수집 중이거나 수집에 실패했습니다. python lab.py status 로 확인하세요.")
    print("    출처별:", dict(Counter(n["source"] for n in items)))
    print("    주제별:", dict(Counter(n["topic"] for n in items)))
    print("    마감 상태:", dict(Counter(n["deadline_status"] for n in items)))
    print("    요약 있음:", sum(1 for n in items if n.get("summary")), "건 / 대상 조건 있음:",
          sum(1 for n in items if any((n.get("eligibility") or {}).get(k) for k in
              ("colleges", "depts", "grades", "min_semester", "max_semester", "levels", "statuses", "gender", "nationality", "min_gpa"))), "건")
    print("    목록에 body 가 섞여 있음:", sum(1 for n in items if "body" in n), "건 (0 이어야 정상)")

    # 2) 팀 공통 형식(notice.schema.json)에 맞는지 검사
    try:
        import jsonschema
        schema = json.load(open("notice.schema.json", encoding="utf-8"))
        v = jsonschema.Draft202012Validator(schema)
        bad = [(n["id"], list(v.iter_errors(n))[0].message[:100]) for n in items if list(v.iter_errors(n))]
        print(f"[2] 공통 형식 검사: 통과 {len(items) - len(bad)}건 / 오류 {len(bad)}건")
        for i, m in bad[:5]:
            print("    ", i, "→", m)
    except ImportError:
        print("[2] jsonschema 가 없어 형식 검사를 건너뜀")

    # 3) 상세에서 본문이 오는지 (공지 10건 표본)
    sample = items[:: max(1, len(items) // 10)][:10]
    ok = 0
    print("[3] 상세(본문) 받기: 표본", len(sample), "건")
    for n in sample:
        d = requests.get(f"{URL}/v1/notices/{n['id']}", timeout=30).json()
        body = d.get("body") or ""
        ok += bool(body)
        print(f"    {'본문 있음' if body else '본문 없음'} ({len(body):5d}자) [{d.get('content_kind')}] {d['title'][:36]}")
    print(f"    → 본문이 있는 공지 {ok}/{len(sample)}건 (이미지·첨부만 있는 공지는 본문이 비어 있을 수 있음)")

    # 4) 바뀐 것만 오는지
    r = requests.get(f"{URL}/v1/notices", params={"since": since}, timeout=30).json()
    print(f"[4] 방금 받은 next_version({since}) 이후 변경분: {len(r['items'])}건 (0 이면 정상 = 새로 바뀐 게 없음)")

    json.dump(items, open("lab_pulled.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("\n받은 공지를 lab_pulled.json 에 저장했습니다. 메모장이나 VS Code 로 열어 볼 수 있습니다.")


DATA_FILES = ["hyetaek.db", "step3_notices.json", "job_notices.json", "program_notices.json", "step4_results.json",
              "archive_step3_notices.json", "archive_job_notices.json", "archive_program_notices.json",
              "feed.json", "common_notices.json", "lab_pulled.json"]


def cmd_doctor():
    import importlib
    import socket
    ok = True

    def line(good, text):
        nonlocal ok
        ok = ok and good
        print(("  [OK]   " if good else "  [확인] ") + text)

    print("1) 파이썬")
    v = sys.version_info
    line(v >= (3, 10), f"Python {v.major}.{v.minor}.{v.micro} (3.10 이상 필요) / 실행 위치: {sys.executable}")
    in_venv = sys.prefix != getattr(sys, "base_prefix", sys.prefix)
    line(in_venv, "가상환경(.venv) 안에서 실행 중" if in_venv
         else "가상환경이 꺼져 있음 → .\\.venv\\Scripts\\Activate.ps1 먼저")
    print("2) 패키지")
    for mod in ("requests", "bs4", "lxml", "jsonschema", "fastapi", "uvicorn", "apscheduler", "olefile"):
        try:
            importlib.import_module(mod)
            line(True, mod)
        except ImportError:
            line(False, f"{mod} 없음 → pip install -r requirements.txt")
    print("3) Gemini (AI)")
    key, model = os.environ.get("GEMINI_API_KEY", ""), os.environ.get("GEMINI_MODEL", "")
    line(bool(key), f"GEMINI_API_KEY 설정됨 ({key[:4]}…{key[-2:]}, {len(key)}자)" if key
         else "GEMINI_API_KEY 없음 → setx 로 넣고 VS Code를 완전히 껐다 켜기 (AI 없이도 수집은 됨)")
    line(bool(model), f"GEMINI_MODEL = {model}" if model else "GEMINI_MODEL 없음 → setx GEMINI_MODEL \"모델이름\"")
    if key and model:
        try:
            r = requests.get("https://generativelanguage.googleapis.com/v1beta/models", headers={"x-goog-api-key": key},
                             params={"pageSize": 200}, timeout=20)
            names = [m["name"].replace("models/", "") for m in r.json().get("models", [])]
            line(r.status_code == 200, "키 확인됨" if r.status_code == 200 else f"키 오류 HTTP {r.status_code}")
            line(model in names, f"모델 '{model}' 사용 가능" if model in names
                 else f"모델 '{model}' 이 목록에 없음 → python step4_extract.py --list-models 로 이름 확인")
        except requests.RequestException as e:
            line(False, f"Gemini 접속 실패: {type(e).__name__}")
    print("4) 학교 사이트 접속")
    for u in ("https://scatch.ssu.ac.kr", "https://job.ssu.ac.kr"):
        try:
            r = requests.get(u, timeout=15, headers={"User-Agent": "SSU-Hyetaek-Student-Project (mobile programming class)"})
            line(r.status_code < 500, f"{u} → HTTP {r.status_code}")
        except requests.RequestException as e:
            line(False, f"{u} → 접속 실패 {type(e).__name__} (학교 사이트가 느리거나 막힘)")
    print("5) 네트워크")
    ips = set()
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ips.add(s.getsockname()[0])
        s.close()
    except OSError:
        pass
    print("  이 컴퓨터 IP(앱에 넣을 주소 후보):", ", ".join(f"http://{ip}:8000/" for ip in sorted(ips)) or "확인 실패 → ipconfig")
    try:
        requests.get(f"{URL}/v1/status", timeout=3)
        print("  서버: 이미 켜져 있음 (8000번 사용 중)")
    except requests.RequestException:
        print("  서버: 아직 꺼져 있음 → .\\run_lab.ps1 -Bind 0.0.0.0 으로 켜기")
    print("\n결과:", "준비 완료" if ok else "[확인] 줄을 먼저 해결하세요 (Gemini 항목은 AI 없이 시험할 거면 넘어가도 됨)")


def cmd_reset():
    try:
        requests.get(f"{URL}/v1/status", timeout=3)
        sys.exit("서버가 켜져 있습니다. 서버 창에서 Ctrl+C 로 먼저 끄세요.")
    except requests.RequestException:
        pass
    have = [f for f in DATA_FILES if os.path.exists(f)]
    if not have:
        print("지울 데이터가 없습니다. (이미 처음 상태)")
        return
    print("지울 파일:", ", ".join(have))
    if "--yes" not in sys.argv and input("정말 지울까요? 코드와 .venv 는 그대로 남습니다 (y/N): ").strip().lower() != "y":
        print("취소했습니다.")
        return
    for f in have:
        os.remove(f)
    print(f"{len(have)}개 삭제. 이제 서버를 켜면 처음부터 다시 수집합니다. 앱에서는 [처음부터]를 누르세요.")


if __name__ == "__main__":
    cmds = {"status": cmd_status, "collect": cmd_collect, "verify": cmd_verify, "doctor": cmd_doctor, "reset": cmd_reset}
    if len(sys.argv) < 2 or sys.argv[1] not in cmds:
        sys.exit(__doc__)
    cmds[sys.argv[1]]()
