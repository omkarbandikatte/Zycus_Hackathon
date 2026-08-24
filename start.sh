#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # Load local provider credentials without committing them.
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

STRATEGY="${STOCKPULSE_STRATEGY:-rules}"

# Git Bash can inherit a JRE-only Java from Windows. Prefer an installed JDK so Maven can compile.
if ! command -v javac >/dev/null 2>&1; then
  for candidate in \
    "/c/Program Files/Eclipse Adoptium"/jdk-* \
    "/c/Program Files/Microsoft"/jdk-* \
    "/c/Program Files/Java"/jdk-*; do
    if [[ -x "$candidate/bin/javac.exe" ]]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      break
    fi
  done
fi

if ! command -v javac >/dev/null 2>&1; then
  echo "A full JDK is required. Install JDK 17 or 21 and set JAVA_HOME before starting StockPulse."
  exit 1
fi

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  echo "Frontend dependencies are missing. Run: cd frontend && npm install"
  exit 1
fi

cleanup() {
  trap - INT TERM EXIT
  [[ -n "${BACKEND_PID:-}" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  [[ -n "${FRONTEND_PID:-}" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup INT TERM EXIT

if curl --silent --fail --max-time 2 http://localhost:8080/products >/dev/null 2>&1; then
  echo "Reusing StockPulse backend on http://localhost:8080"
else
  echo "Starting StockPulse backend on http://localhost:8080"
  (
    cd "$BACKEND_DIR"
    mvn spring-boot:run "-Dspring-boot.run.arguments=--stockpulse.strategy=$STRATEGY"
  ) &
  BACKEND_PID=$!
fi

if curl --silent --fail --max-time 2 http://localhost:5173/ | grep -q "StockPulse"; then
  echo "Reusing StockPulse frontend on http://localhost:5173"
else
  echo "Starting StockPulse frontend on http://localhost:5173"
  (
    cd "$FRONTEND_DIR"
    npm run dev -- --host 0.0.0.0
  ) &
  FRONTEND_PID=$!
fi

PIDS=()
[[ -n "${BACKEND_PID:-}" ]] && PIDS+=("$BACKEND_PID")
[[ -n "${FRONTEND_PID:-}" ]] && PIDS+=("$FRONTEND_PID")
if ((${#PIDS[@]} > 0)); then
  wait -n "${PIDS[@]}"
  EXIT_CODE=$?
else
  EXIT_CODE=0
fi
cleanup
exit "$EXIT_CODE"
