#!/usr/bin/env bash
# Stops what start-demo.sh started, then Camunda 8 Run.
C8RUN="${C8RUN:-$HOME/camunda/c8run-8.10.6}"
cd "$(dirname "$0")"

for name in frontend backend mock-services mailpit; do
  if [ -f "logs/$name.pid" ]; then
    pkill -P "$(cat "logs/$name.pid")" 2>/dev/null   # child processes (java under mvn, vite under npm)
    kill "$(cat "logs/$name.pid")" 2>/dev/null
    rm "logs/$name.pid"
    echo "$name stopped"
  fi
done

(cd "$C8RUN" && ./c8run stop)
echo "Camunda stopped."
