#!/usr/bin/env bash
# Starts the ERENO backend (api/) and the ERENO UI (app/, port 5173).
# Usage, from the repository root:
#   ./dev.sh                    backend on 8080, rebuilt only when its sources changed
#   ERENO_API_PORT=8081 ./dev.sh   use another port when 8080 is taken
#   ./dev.sh --rebuild          force a backend rebuild
# Ctrl+C stops both.
set -euo pipefail

root="$(cd "$(dirname "$0")" && pwd)"
api="$root/api"
app="$root/app"
jar="$api/target/ERENO-1.0-SNAPSHOT-uber.jar"
port="${ERENO_API_PORT:-8080}"

stale() {
  [ ! -f "$jar" ] && return 0
  [ -n "$(find "$api/src/main" "$api/pom.xml" -newer "$jar" -type f -print -quit)" ]
}

if curl -s -o /dev/null "http://127.0.0.1:$port/"; then
  echo "Port $port is already in use. Pick another one: ERENO_API_PORT=8081 ./dev.sh" >&2
  exit 1
fi

if [ "${1:-}" = "--rebuild" ] || stale; then
  echo "==> Building the backend (mvn package, tests skipped)..."
  if command -v mvn >/dev/null 2>&1; then mvn_cmd=mvn; else mvn_cmd=./mvnw; fi
  (cd "$api" && $mvn_cmd -q -B -DskipTests package)
fi

if [ ! -d "$app/node_modules" ]; then
  echo "==> Installing frontend dependencies (npm install)..."
  (cd "$app" && npm install)
fi

echo "==> Starting the backend on http://localhost:$port ..."
(cd "$api" && exec java "-Dereno.api.port=$port" -jar "$jar" serve) &
backend=$!
trap 'echo "==> Stopping the backend..."; kill "$backend" 2>/dev/null || true' EXIT INT TERM

for _ in $(seq 1 60); do
  curl -sf -o /dev/null "http://127.0.0.1:$port/api/health" && break
  kill -0 "$backend" 2>/dev/null || { echo "The backend did not start; see the messages above." >&2; exit 1; }
  sleep 0.5
done

echo "==> Starting the UI on http://localhost:5173 (Ctrl+C stops both)"
cd "$app" && VITE_API_URL="http://localhost:$port" npm run dev
