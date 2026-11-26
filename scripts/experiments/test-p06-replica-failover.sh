#!/usr/bin/env bash
# P06 — Fallo de réplica PostgreSQL: lecturas antes, inmediatamente durante y
# tras la detección; incluye estado de contenedores y logs del selector
# (db.route) de las instancias de Orders. Restaura la réplica al final.
#
# Uso: bash scripts/experiments/test-p06-replica-failover.sh [archivo_salida]
# Variables: GATEWAY_URL, REPLICA (def. postgres-replica1), BEFORE (10),
#            DURING (40), INTERVAL (0.25 s).
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
REPLICA="${REPLICA:-postgres-replica1}"
BEFORE="${BEFORE:-10}"
DURING="${DURING:-40}"
INTERVAL="${INTERVAL:-0.25}"
OUT="${1:-docs/evidencias/P06-replica-failover-full.log}"
URL="$GATEWAY_URL/api/orders"
ORDERS=(order-service-1 order-service-2)

now_ms() { date +%s%3N; }

probe() {
  local headers status inst node
  headers="$(curl --silent --dump-header - --output /dev/null \
    --connect-timeout 3 --max-time 10 "$URL" 2>/dev/null || true)"
  status="$(printf '%s' "$headers" | awk 'toupper($1) ~ /^HTTP/ {s=$2} END {print (s==""?"000":s)}')"
  inst="$(printf '%s' "$headers" | awk -F': ' 'tolower($1)=="x-instance-id" {gsub("\r","",$2); print $2}')"
  node="$(printf '%s' "$headers" | awk -F': ' 'tolower($1)=="x-database-node" {gsub("\r","",$2); print $2}')"
  printf '%s\t%s\t%s' "$status" "${inst:-MISSING}" "${node:-MISSING}"
}

phase() {
  local name="$1" n="$2" ref="$3" i r ok=0 err=0 r1=0 r2=0
  echo "phase=$name"
  printf 'request\telapsed_ms\thttp_status\tinstance_id\tdatabase_node\n'
  for i in $(seq 1 "$n"); do
    r="$(probe)"
    printf '%s\t%s\t%s\n' "$i" "$(( $(now_ms) - ref ))" "$r"
    [[ "${r%%$'\t'*}" == "200" ]] && ok=$((ok+1)) || err=$((err+1))
    [[ "$r" == *REPLICA_1 ]] && r1=$((r1+1))
    [[ "$r" == *REPLICA_2 ]] && r2=$((r2+1))
    sleep "$INTERVAL"
  done
  echo "summary_$name: ok=$ok errors=$err REPLICA_1=$r1 REPLICA_2=$r2"
  echo
}

containers() {
  docker ps -a --filter name=postgres --format '{{.Names}}\t{{.Status}}' | sort
}

{
  echo "P06 — fallo de réplica $REPLICA (antes / durante / logs del selector)"
  echo "fecha=$(date -Iseconds) url=$URL"
  echo
  echo "containers_before:"; containers; echo

  t0=$(now_ms)
  phase before "$BEFORE" "$t0"

  since="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "docker stop $REPLICA"
  t_stop=$(now_ms)
  # En segundo plano: las solicitudes "durante" cubren también el apagado ordenado.
  docker stop --timeout 40 "$REPLICA" >/dev/null &
  stop_pid=$!
  phase during "$DURING" "$t_stop"
  wait "$stop_pid"; echo "stop_completed_ms=$(( $(now_ms) - t_stop ))"
  echo "containers_during:"; containers; echo

  echo "selector_logs (db.route y estado de réplicas desde $since):"
  for svc in "${ORDERS[@]}"; do
    docker logs --since "$since" "$svc" 2>&1 \
      | grep -E 'db\.route|replica\.(state|rotation)' | grep -v 'result=OK' | head -40 \
      | sed "s/^/[$svc] /" || true
  done
  echo

  echo "docker start $REPLICA"
  t_start=$(now_ms)
  docker start "$REPLICA" >/dev/null
  while (( $(now_ms) - t_start < 180000 )); do
    [[ "$(docker inspect -f '{{.State.Health.Status}}' "$REPLICA")" == healthy ]] && break
    sleep 1
  done
  echo "restored_health=$(docker inspect -f '{{.State.Health.Status}}' "$REPLICA") after_ms=$(( $(now_ms) - t_start ))"
  echo "containers_after:"; containers
} | tee "$OUT"
