#!/usr/bin/env bash
set -euo pipefail
if [ ! -s "$PGDATA/PG_VERSION" ]; then
  mkdir -p "$PGDATA"
  chmod 700 "$PGDATA"
  echo 'Esperando al primario y creando copia base...'
  until pg_basebackup -h primary -p 5432 -U replicator -D "$PGDATA" -Fp -Xs -R -P; do
    echo 'Copia fallida; reintentando en 3 segundos...'
    rm -rf "${PGDATA:?}"/*
    sleep 3
  done
fi
exec postgres -D "$PGDATA" -c hot_standby=on
