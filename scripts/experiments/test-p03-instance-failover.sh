#!/usr/bin/env bash
# P03 — Fallo de instancia: solicitudes antes, durante y después de detener
# order-service-1, y tiempo de recuperación medido.
#
# Uso: bash scripts/experiments/test-p03-instance-failover.sh [archivo_salida]
# Variables: GATEWAY_URL (def. http://localhost:8080), TARGET (def. order-service-1),
#            BEFORE (20), DURING (40), AFTER (20), INTERVAL (0.25 s).
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
TARGET="${TARGET:-order-service-1}"
BEFORE="${BEFORE:-20}"
DURING="${DURING:-40}"
AFTER="${AFTER:-20}"
INTERVAL="${INTERVAL:-0.25}"
OUT="${1:-docs/evidencias/P03-instance-failover-full.log}"
URL="$GATEWAY_URL/api/orders"

now_ms() { date +%s%3N; }

# Devuelve: http_status<TAB>instance_id<TAB>database_node
probe() {
  local headers status inst node
  headers="$(curl --silent --dump-header - --output /dev/null \
    --connect-timeout 3 --max-time 10 "$URL" 2>/dev/null || true)"
  status="$(printf '%s' "$headers" | awk 'toupper($1) ~ /^HTTP/ {s=$2} END {print (s==""?"000":s)}')"
  inst="$(printf '%s' "$headers" | awk -F': ' 'tolower($1)=="x-instance-id" {gsub("\r","",$2); print $2}')"
  node="$(printf '%s' "$headers" | awk -F': ' 'tolower($1)=="x-database-node" {gsub("\r","",$2); print $2}')"
  printf '%s\t%s\t%s' "$status" "${inst:-MISSING}" "${node:-MISSING}"
}

phase() { # nombre, cantidad, t_ref
  local name="$1" n="$2" ref="$3" i r ok=0 err=0
  echo "phase=$name"
  printf 'request\telapsed_ms\thttp_status\tinstance_id\tdatabase_node\n'
  for i in $(seq 1 "$n"); do
    r="$(probe)"
    printf '%s\t%s\t%s\n' "$i" "$(( $(now_ms) - ref ))" "$r"
    [[ "${r%%$'\t'*}" == "200" ]] && ok=$((ok+1)) || err=$((err+1))
    sleep "$INTERVAL"
  done
  echo "summary_$name: ok=$ok errors=$err"
  echo
}

{
  echo "P03 — fallo de instancia $TARGET (antes / durante / después)"
  echo "fecha=$(date -Iseconds) url=$URL"
  echo

  # Readiness: ambas instancias deben haber respondido 200 antes de medir.
  seen1=0; seen2=0; t=$(now_ms)
  while (( $(now_ms) - t < 180000 )); do
    r="$(probe)"
    [[ "$r" == 200$'\t'order-service-1* ]] && seen1=1
    [[ "$r" == 200$'\t'order-service-2* ]] && seen2=1
    (( seen1 && seen2 )) && break
    sleep 0.5
  done
  echo "readiness_gate: order-service-1=$seen1 order-service-2=$seen2"
  echo

  t0=$(now_ms)
  phase before "$BEFORE" "$t0"

  echo "docker stop $TARGET"
  t_stop=$(now_ms)
  # En segundo plano: las solicitudes "durante" cubren también el apagado ordenado.
  docker stop --timeout 40 "$TARGET" >/dev/null &
  stop_pid=$!
  echo
  phase during "$DURING" "$t_stop"
  wait "$stop_pid"; echo "stop_completed_ms=$(( $(now_ms) - t_stop ))"
  echo

  echo "docker start $TARGET"
  t_start=$(now_ms)
  docker start "$TARGET" >/dev/null
  healthy_ms=""; back_ms=""
  while (( $(now_ms) - t_start < 300000 )); do
    if [[ -z "$healthy_ms" ]] && \
       [[ "$(docker inspect -f '{{.State.Health.Status}}' "$TARGET")" == healthy ]]; then
      healthy_ms=$(( $(now_ms) - t_start ))
    fi
    r="$(probe)"
    if [[ "$r" == 200$'\t'"$TARGET"* ]]; then back_ms=$(( $(now_ms) - t_start )); break; fi
    sleep 0.5
  done
  while [[ -z "$healthy_ms" ]] && (( $(now_ms) - t_start < 300000 )); do
    [[ "$(docker inspect -f '{{.State.Health.Status}}' "$TARGET")" == healthy ]] \
      && healthy_ms=$(( $(now_ms) - t_start )) || sleep 0.5
  done
  echo "recovery_container_healthy_ms=${healthy_ms:-timeout}"
  echo "recovery_first_200_from_${TARGET}_ms=${back_ms:-timeout}"
  echo

  phase after "$AFTER" "$t_start"
} | tee "$OUT"
