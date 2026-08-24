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

echo "Starting StockPulse backend on http://localhost:8080"
(
  cd "$BACKEND_DIR"
  mvn spring-boot:run "-Dspring-boot.run.arguments=--stockpulse.strategy=$STRATEGY"
) &
BACKEND_PID=$!

echo "Starting StockPulse frontend on http://localhost:5173"
(
  cd "$FRONTEND_DIR"
  npm run dev -- --host 0.0.0.0
) &
FRONTEND_PID=$!

wait -n "$BACKEND_PID" "$FRONTEND_PID"
EXIT_CODE=$?
cleanup
exit "$EXIT_CODE"
