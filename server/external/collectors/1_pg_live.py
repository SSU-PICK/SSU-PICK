"""평강 수집기(SSUPICK/tools/ssupick_export_v1.py)로 LH·마이홈(·온통청년)을 실제 API로 다시 받아
external/pg_data/*_live.json 으로 놓는다. 그다음 2_pg_convert.py 가 우리 형식으로 바꿔 DB에 합친다.

서버가 수집할 때마다(collect_all.py) 자동으로 실행된다. 직접 시험 (server-lab 폴더에서):
    .venv/bin/python external/collectors/1_pg_live.py
필요한 것
  - 평강 코드 폴더: ~/ssu-pick/SSUPICK  (다른 곳이면 환경변수 PG_ROOT)
  - API 키 5개: /etc/ssu-pick.env (LH_API_KEY, MYHOME_API_KEY, MYHOME_API_ENDPOINT, DATA_GO_KR_KEY, YOUTH_POLICY_API_KEY)
선택 환경변수
  - PG_LIVE=0            실시간 수집 끄기 (평강이 준 파일만 사용)
  - PG_LIVE_ONTONG=0     온통청년 실시간 수집 끄기 (기본 켬. 전국 정책 중 서울·전국 대상 + 마감 안 지난 것만 2_pg_convert 가 넣음)
  - PG_LIVE_MIN_HOURS=3  마지막 성공 후 이 시간이 안 지났으면 건너뜀 (API 호출 횟수 줄이기)
실패하면 예전 파일을 그대로 두고(앱 데이터는 그대로) 이유만 출력한다. 키는 출력하지 않는다."""
from __future__ import annotations

import glob
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

HERE = Path.cwd()                                           # server-lab
PG_ROOT = Path(os.environ.get("PG_ROOT") or HERE.parent / "SSUPICK").resolve()
DATA = HERE / "external" / "pg_data"
STAMP = DATA / ".live_last_ok"
KEYS = ("LH_API_KEY", "MYHOME_API_KEY", "MYHOME_API_ENDPOINT", "DATA_GO_KR_KEY", "YOUTH_POLICY_API_KEY")


def load_env_file(path="/etc/ssu-pick.env"):
    """서비스로 돌 때는 systemd가 키를 넣어 준다. 손으로 시험할 때를 위해 파일에서도 읽는다(읽을 수 있을 때만)."""
    try:
        lines = open(path, encoding="utf-8").read().splitlines()
    except OSError:
        return
    for ln in lines:
        ln = ln.strip()
        if not ln or ln.startswith("#") or "=" not in ln:
            continue
        k, v = ln.split("=", 1)
        k, v = k.strip(), v.strip().strip('"').strip("'")
        if k in KEYS and v and not os.environ.get(k):
            os.environ[k] = v


def run_export(sources: str, out: Path) -> bool:
    py = PG_ROOT / ".venv" / "bin" / "python"
    py = str(py) if py.exists() else sys.executable
    runner = HERE / "external" / "pg_export_patched.py"           # 평강 수집기 + 실제 API에서 확인된 문제 2개 수정 (평강 파일은 그대로)
    script = str(runner) if runner.exists() else "tools/ssupick_export_v1.py"
    cmd = [py, script, "--root", ".", "--mode", "live", "--sources", sources, "--output-dir", str(out)]
    print(f"  평강 수집기 실행: {sources}", flush=True)
    try:
        r = subprocess.run(cmd, cwd=PG_ROOT, timeout=900, capture_output=True, text=True)
    except subprocess.TimeoutExpired:
        print("  실패: 15분 안에 끝나지 않음")
        return False
    for ln in (r.stdout + r.stderr).splitlines()[-6:]:
        print("   |", ln)
    return r.returncode == 0


def take(out: Path, source: str) -> int:
    """평강 결과(<시각>/<출처>_live_v1_UNVERIFIED.json) → pg_data/<출처>_live.json"""
    hits = sorted(glob.glob(str(out / "*" / f"{source}_live_v1_UNVERIFIED.json")))
    if not hits:
        return 0
    n = len(json.load(open(hits[-1], encoding="utf-8")).get("notices") or [])
    if n:
        shutil.copyfile(hits[-1], DATA / f"{source}_live.json")
    return n


def main() -> int:
    if os.environ.get("PG_LIVE", "1") == "0":
        print("평강 실시간 수집 꺼짐(PG_LIVE=0)")
        return 0
    if not (PG_ROOT / "tools" / "ssupick_export_v1.py").exists():
        print(f"평강 코드 없음({PG_ROOT}) → 실시간 수집 건너뜀 (평강이 준 파일만 사용)")
        return 0
    load_env_file()
    if not (os.environ.get("LH_API_KEY") or os.environ.get("DATA_GO_KR_KEY")):
        print("API 키 없음(/etc/ssu-pick.env) → 실시간 수집 건너뜀")
        return 0
    min_h = float(os.environ.get("PG_LIVE_MIN_HOURS", "3"))
    if STAMP.exists() and time.time() - STAMP.stat().st_mtime < min_h * 3600 - 600:
        print(f"평강 실시간 수집: 마지막 성공 후 {min_h:g}시간이 안 지나 건너뜀")
        return 0
    DATA.mkdir(parents=True, exist_ok=True)
    ok = True
    with tempfile.TemporaryDirectory(prefix="pg_live_") as tmp:
        out = Path(tmp)
        for src, name in (("lh", "LH"), ("myhome", "마이홈")):          # 따로 돌려서 하나가 실패해도 다른 하나는 반영
            if run_export(src, out / src):
                print(f"  {name} {take(out / src, src)}건 받음")
            else:
                ok = False
                print(f"  {name} 실시간 수집 실패 → 예전 데이터 유지")
        if os.environ.get("PG_LIVE_ONTONG", "1") == "1":
            if run_export("ontongyouth", out / "ontongyouth"):
                print(f"  온통청년 {take(out / 'ontongyouth', 'ontongyouth')}건 받음")
            else:
                ok = False
                print("  온통청년 실시간 수집 실패 → 예전 데이터 유지")
    if ok:
        STAMP.touch()
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
