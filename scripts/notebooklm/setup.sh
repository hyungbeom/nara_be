#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
VENV_DIR="$SCRIPT_DIR/.venv"
HOME_DIR="$PROJECT_ROOT/storage/notebooklm"

find_python() {
  for candidate in python3 python; do
    if command -v "$candidate" >/dev/null 2>&1; then
      echo "$candidate"
      return 0
    fi
  done
  echo "Python 3.10+ 가 필요합니다." >&2
  exit 1
}

PYTHON="$(find_python)"

if [ ! -d "$VENV_DIR" ]; then
  "$PYTHON" -m venv "$VENV_DIR"
fi

VENV_PYTHON="$VENV_DIR/bin/python"
"$VENV_PYTHON" -m pip install --upgrade pip
"$VENV_PYTHON" -m pip install -r "$SCRIPT_DIR/requirements.txt"

mkdir -p "$HOME_DIR"

echo ""
echo "NotebookLM Python 환경 설치 완료"
echo "가상환경 Python: $VENV_PYTHON"
echo "인증 저장 경로: $HOME_DIR"
echo ""
echo "클라우드 서버 1회 인증:"
echo "  NOTEBOOKLM_HOME=$HOME_DIR $VENV_PYTHON -m notebooklm login --master-token --account you@gmail.com"
echo ""
