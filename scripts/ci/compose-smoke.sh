#!/usr/bin/env bash
# ==============================================================================
# SMOKE TEST DE INFRAESTRUCTURA (Docker Compose)
# ==============================================================================
# Verifica de extremo a extremo lo que un healthcheck no puede comprobar:
#   1. Primario con 2 réplicas en streaming (credenciales, rol replicator, pg_hba.conf).
#   2. Réplicas en modo recuperación y de solo lectura.
#   3. Datos del primario presentes en las réplicas.
#   4. Lecturas ordinarias del Gateway servidas desde REPLICA_1 y REPLICA_2.
#
# Requisito: el clúster ya levantado y healthy:
#   docker compose up -d --build --wait
#
# Uso:  ./scripts/ci/compose-smoke.sh
# Lo ejecuta también .github/workflows/ci-compose-smoke.yml.
# ==============================================================================
set -euo pipefail

PG_ADMIN_USER="${POSTGRES_USER:-admin}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
REPLICAS=(postgres-replica1 postgres-replica2)
EXPECTED_REPLICAS=${#REPLICAS[@]}

pass() { echo "  [OK]   $*"; }
fail() { echo "  [FAIL] $*" >&2; exit 1; }

psql_in() { # contenedor, base, sql
  docker exec "$1" psql -U "$PG_ADMIN_USER" -d "$2" -tAc "$3"
}

echo "1) Replicación en el primario"
streaming=$(psql_in postgres-primary postgres \
  "SELECT count(*) FROM pg_stat_replication WHERE state = 'streaming'")
[ "$streaming" -eq "$EXPECTED_REPLICAS" ] \
  && pass "$streaming réplicas en streaming" \
  || fail "se esperaban $EXPECTED_REPLICAS réplicas en streaming, hay $streaming"

echo "2) Réplicas en recuperación y de solo lectura"
for replica in "${REPLICAS[@]}"; do
  [ "$(psql_in "$replica" postgres "SELECT pg_is_in_recovery()")" = "t" ] \
    && pass "$replica en modo recuperación" \
    || fail "$replica no está en modo recuperación"
  if docker exec "$replica" psql -U "$PG_ADMIN_USER" -d inventory_db \
      -c "UPDATE inventory_products SET stock = stock" >/dev/null 2>&1; then
    fail "$replica aceptó una escritura"
  fi
  pass "$replica rechaza escrituras"
done

echo "3) Datos replicados"
for replica in "${REPLICAS[@]}"; do
  count=$(psql_in "$replica" inventory_db "SELECT count(*) FROM inventory_products")
  [ "$count" -ge 5 ] \
    && pass "$replica tiene $count productos" \
    || fail "$replica tiene $count productos (se esperaban >= 5)"
done

echo "4) Lecturas por el Gateway desde réplicas"
# Eureka y la caché del LoadBalancer tardan unos segundos tras el healthy.
for attempt in $(seq 1 30); do
  curl -fs -o /dev/null "$GATEWAY_URL/api/orders" && break
  [ "$attempt" -eq 30 ] && fail "el Gateway no enruta /api/orders tras 150 s"
  sleep 5
done
nodes=""
for _ in $(seq 1 10); do
  node=$(curl -fs -D - -o /dev/null "$GATEWAY_URL/api/orders" \
    | grep -i '^X-Database-Node:' | awk '{print $2}' | tr -d '\r') || true
  nodes="$nodes $node"
done
echo "       nodos observados:$nodes"
for expected in REPLICA_1 REPLICA_2; do
  echo "$nodes" | grep -q "$expected" \
    && pass "lecturas servidas por $expected" \
    || fail "ninguna lectura servida por $expected"
done

echo "Smoke test superado."
