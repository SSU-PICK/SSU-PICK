# 서버 실습용 실행 파일. 실제 서버(systemd)가 하는 일을 내 컴퓨터에서 똑같이 한다.
#  - 환경변수로 설정을 주고, uvicorn 으로 main.py 를 켠다
#  - DB 가 비어 있으면 켜자마자 첫 수집(슈캐치 카테고리당 10개, 잡슈 목록당 10개)을 시작한다
# 실행:  .\run_lab.ps1
# 끄기:  Ctrl + C
# 다른 기기(폰·노트북)에서 와이파이로 접속하게 하려면:  .\run_lab.ps1 -Bind 0.0.0.0
param([string]$Bind = "127.0.0.1")     # 기본: 이 컴퓨터(+에뮬레이터, USB 연결 폰)에서만 접속 가능

$env:ADMIN_KEY = "lab-secret"          # 수동 수집 비밀번호(연습용). lab.py collect 도 같은 값을 쓴다
$env:COLLECT_CATCH = "10"              # 슈캐치: 카테고리당 10개
$env:COLLECT_DETAIL = "10"             # 잡슈: 목록마다 상세 10개
$env:COLLECT_PAGES = "1"
$env:COLLECT_EVERY_MINUTES = "30"      # 시험용: 30분마다 자동 수집 (실제 서버는 이 줄 없이 하루 6번)
$env:COLLECT_AI = "1"                  # 0 으로 바꾸면 AI(Gemini)를 건너뛰어 빨라진다
$env:AI_IMAGES = "1"                   # 1: 그림(포스터)·PDF 첨부도 AI가 읽는다 / 0: 글만 읽는다
# GEMINI_API_KEY, GEMINI_MODEL 은 setx 로 이미 넣어 둔 값을 그대로 쓴다 (여기에 적지 않는다)

if (-not (Test-Path ".\.venv\Scripts\Activate.ps1")) {
    Write-Host "가상환경(.venv)이 없습니다. 먼저 .\setup_lab.ps1 을 실행하세요 (가이드 2장)." -ForegroundColor Red
    exit 1
}
if (-not $env:GEMINI_API_KEY) {
    Write-Host "참고: GEMINI_API_KEY 가 없어 AI 단계는 건너뜁니다 (수집·저장·앱 연결은 그대로 됩니다)." -ForegroundColor Yellow
}
.\.venv\Scripts\Activate.ps1
uvicorn main:app --host $Bind --port 8000
