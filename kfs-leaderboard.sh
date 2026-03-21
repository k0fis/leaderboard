#!/usr/bin/env bash
###############################################################################
# kfs-leaderboard.sh - Spouštěcí script s watchdogem
#
# Spouští kfsLeaderboard.jar a hlídá health endpoint.
# Pokud server neodpovídá, restartuje ho.
#
# Použití:
#   ./kfs-leaderboard.sh start              # spustí na portu 8080 (default)
#   ./kfs-leaderboard.sh -p 9090 start      # spustí na portu 9090
#   ./kfs-leaderboard.sh stop               # zastaví server + watchdog
#   ./kfs-leaderboard.sh status             # stav serveru
#   ./kfs-leaderboard.sh restart            # restart
#   ./kfs-leaderboard.sh log                # tail logu
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/kfsLeaderboard.jar"
PORT=8080

# Parse -p PORT
while getopts "p:" opt; do
  case $opt in
    p) PORT="$OPTARG" ;;
    *) echo "Použití: $0 [-p port] {start|stop|restart|status|log}"; exit 1 ;;
  esac
done
shift $((OPTIND - 1))

PID_FILE="$SCRIPT_DIR/leaderboard.pid"
WATCHDOG_PID_FILE="$SCRIPT_DIR/watchdog.pid"
LOG_FILE="$SCRIPT_DIR/leaderboard.log"
HEALTH_URL="http://localhost:$PORT/api/health"
CHECK_INTERVAL=60       # kontola kazdych 60s
STARTUP_WAIT=15         # po startu cekat 15s nez zacne kontrolovat
MAX_FAILURES=3          # po 3 selhanich restartovat

# --- Funkce ---

is_running() {
    [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null
}

watchdog_running() {
    [ -f "$WATCHDOG_PID_FILE" ] && kill -0 "$(cat "$WATCHDOG_PID_FILE")" 2>/dev/null
}

start_server() {
    if is_running; then
        echo "Server už běží (PID $(cat "$PID_FILE"))"
        return 1
    fi

    if [ ! -f "$JAR" ]; then
        echo "CHYBA: $JAR neexistuje"
        exit 1
    fi

    echo "Spouštím server..."
    nohup java -jar "$JAR" --server.port="$PORT" >> "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    echo "Server spuštěn (PID $!, port $PORT)"
}

stop_server() {
    if is_running; then
        local pid
        pid=$(cat "$PID_FILE")
        echo "Zastavuji server (PID $pid)..."
        kill "$pid"
        # Počkat max 10s na graceful shutdown
        for i in $(seq 1 10); do
            if ! kill -0 "$pid" 2>/dev/null; then
                break
            fi
            sleep 1
        done
        # Pokud stále běží, kill -9
        if kill -0 "$pid" 2>/dev/null; then
            echo "  Forceful kill..."
            kill -9 "$pid" 2>/dev/null || true
        fi
        rm -f "$PID_FILE"
        echo "Server zastaven."
    else
        echo "Server neběží."
        rm -f "$PID_FILE"
    fi
}

stop_watchdog() {
    if watchdog_running; then
        local pid
        pid=$(cat "$WATCHDOG_PID_FILE")
        echo "Zastavuji watchdog (PID $pid)..."
        kill "$pid" 2>/dev/null || true
        rm -f "$WATCHDOG_PID_FILE"
    fi
}

check_health() {
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$HEALTH_URL" 2>/dev/null || echo "000")
    [ "$http_code" = "200" ]
}

run_watchdog() {
    local failures=0

    # Počkat na startup
    sleep "$STARTUP_WAIT"

    while true; do
        if ! is_running; then
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] Proces neběží, restartuji..." >> "$LOG_FILE"
            start_server
            failures=0
            sleep "$STARTUP_WAIT"
            continue
        fi

        if check_health; then
            failures=0
        else
            failures=$((failures + 1))
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] Health check selhal ($failures/$MAX_FAILURES)" >> "$LOG_FILE"

            if [ "$failures" -ge "$MAX_FAILURES" ]; then
                echo "[$(date '+%Y-%m-%d %H:%M:%S')] $MAX_FAILURES selhání, restartuji server..." >> "$LOG_FILE"
                stop_server
                sleep 2
                start_server
                failures=0
                sleep "$STARTUP_WAIT"
                continue
            fi
        fi

        sleep "$CHECK_INTERVAL"
    done
}

show_status() {
    echo "Port:     $PORT"
    if is_running; then
        echo "Server:   BĚŽÍ (PID $(cat "$PID_FILE"))"
    else
        echo "Server:   NEBĚŽÍ"
    fi

    if watchdog_running; then
        echo "Watchdog: BĚŽÍ (PID $(cat "$WATCHDOG_PID_FILE"))"
    else
        echo "Watchdog: NEBĚŽÍ"
    fi

    if check_health; then
        echo "Health:   OK"
        curl -s "$HEALTH_URL" 2>/dev/null
        echo
    else
        echo "Health:   NEODPOVÍDÁ"
    fi
}

# --- Main ---

case "${1:-}" in
    start)
        start_server
        echo "Spouštím watchdog..."
        nohup bash "$0" -p "$PORT" _watchdog >> "$LOG_FILE" 2>&1 &
        echo $! > "$WATCHDOG_PID_FILE"
        echo "Watchdog spuštěn (PID $!)"
        echo "Log: tail -f $LOG_FILE"
        ;;
    _watchdog)
        run_watchdog
        ;;
    stop)
        stop_watchdog
        stop_server
        ;;
    restart)
        stop_watchdog
        stop_server
        sleep 2
        start_server
        echo "Spouštím watchdog..."
        nohup bash "$0" -p "$PORT" _watchdog >> "$LOG_FILE" 2>&1 &
        echo $! > "$WATCHDOG_PID_FILE"
        echo "Watchdog spuštěn (PID $!)"
        ;;
    status)
        show_status
        ;;
    log)
        tail -f "$LOG_FILE"
        ;;
    *)
        echo "Použití: $0 [-p port] {start|stop|restart|status|log}"
        exit 1
        ;;
esac
