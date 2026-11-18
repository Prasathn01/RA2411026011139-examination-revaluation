#!/usr/bin/env bash
# =============================================================
#  Starts the whole system WITHOUT Docker (Camunda 8 Run mode) on macOS / Linux.
#    chmod +x start-demo.sh stop-demo.sh     (once)
#    ./start-demo.sh
#  Logs go to logs/*.log; stop everything with ./stop-demo.sh
#
#  Needs: Camunda 8 Run 8.10.6 in $C8RUN, Java 21, Maven, Node.js, Mailpit (brew install mailpit)
# =============================================================
set -e
C8RUN="${C8RUN:-$HOME/camunda/c8run-8.10.6}"
cd "$(dirname "$0")"
mkdir -p logs

[ -f secrets/secrets.env ] || cp secrets/secrets.example.env secrets/secrets.env

echo "[1/5] Camunda 8 Run"
if curl -s -o /dev/null http://localhost:8080/v2/topology; then
  echo "      already running"
else
  (cd "$C8RUN" && ./c8run start --no-browser)
fi
# Connector secrets (RESULTS_API_URL, SMTP_HOST, ...) used by the REST and Email connectors
"$C8RUN/c8run" secrets import "$PWD/secrets/secrets.env"

echo "[2/5] Mailpit test inbox (http://localhost:8025)"
nohup mailpit --smtp-auth-accept-any --smtp-auth-allow-insecure > logs/mailpit.log 2>&1 & echo $! > logs/mailpit.pid

echo "[3/5] Mock external systems (http://localhost:3000)"
nohup node mock-services/mock-services.js > logs/mock-services.log 2>&1 & echo $! > logs/mock-services.pid

echo "[4/5] Spring Boot backend (http://localhost:8081)"
(cd backend-springboot && nohup mvn -B -q spring-boot:run > ../logs/backend.log 2>&1 & echo $! > ../logs/backend.pid)

echo "[5/5] React portal (http://localhost:5173)"
[ -d frontend-react/node_modules ] || (cd frontend-react && npm install)
(cd frontend-react && nohup npm run dev > ../logs/frontend.log 2>&1 & echo $! > ../logs/frontend.pid)

echo
echo "Starting. The backend needs about 30 seconds (tail -f logs/backend.log)."
echo "  Portal   http://localhost:5173"
echo "  Operate  http://localhost:8080/operate   (demo / demo)"
echo "  Mailpit  http://localhost:8025"
echo "  Mock     http://localhost:3000"
