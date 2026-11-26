# 처음 한 번만: 가상환경(.venv)을 만들고 필요한 패키지를 설치한다.
# 실행:  .\setup_lab.ps1        (server-lab 폴더에서)
# 다시 실행해도 괜찮다 (이미 있으면 패키지만 최신으로 맞춘다)

$py = Get-Command python -ErrorAction SilentlyContinue
if (-not $py) {
    Write-Host "python 명령을 찾을 수 없습니다. 가이드 2-1(파이썬 설치, Add python.exe to PATH 체크)을 보세요." -ForegroundColor Red
    exit 1
}
python --version
if (-not (Test-Path ".\.venv\Scripts\Activate.ps1")) {
    Write-Host "가상환경 만드는 중..." -ForegroundColor Cyan
    python -m venv .venv
}
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
Write-Host ""
Write-Host "설치 끝. 이어서:  python lab.py doctor" -ForegroundColor Green
