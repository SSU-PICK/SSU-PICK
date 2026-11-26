#!/usr/bin/env bash
# SSU:Pick 서버 설치 (EC2 Ubuntu 24.04, 처음 한 번)
# 실행 위치: ~/ssu-pick/server-lab   →   bash deploy/setup_server.sh
set -e
cd "$(dirname "$0")/.."
echo "== 1) 시스템 패키지"
sudo apt-get update -y
sudo apt-get install -y python3-venv python3-pip tmux
echo "== 2) 스왑 1GB (t3.micro 메모리 1GB 보완, 이미 있으면 건너뜀)"
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 1G /swapfile && sudo chmod 600 /swapfile
  sudo mkswap /swapfile && sudo swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi
echo "== 3) 가상환경 + 파이썬 패키지"
[ -d .venv ] || python3 -m venv .venv
.venv/bin/pip install -q --upgrade pip
.venv/bin/pip install -q -r requirements.txt
echo "== 4) 환경 파일 (없을 때만 예시로 만든다)"
if [ ! -f /etc/ssu-pick.env ]; then
  sudo cp deploy/ssu-pick.env.example /etc/ssu-pick.env
  sudo chown "$USER":"$USER" /etc/ssu-pick.env
  sudo chmod 600 /etc/ssu-pick.env
  echo "   /etc/ssu-pick.env 를 만들었습니다 → nano /etc/ssu-pick.env 로 키를 넣으세요"
fi
echo
echo "설치 끝. 다음: nano /etc/ssu-pick.env  →  가이드 4단계"
