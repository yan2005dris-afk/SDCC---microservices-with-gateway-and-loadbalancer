#!/usr/bin/env bash
# Inicializa una réplica física (hot standby) a partir del primario y arranca PostgreSQL.
#
# Política de errores durante la copia base (pg_basebackup):
#   - Transitorios (primario aún no disponible: connection refused, DNS, timeout)
#       -> se reintenta cada BASEBACKUP_RETRY_DELAY segundos.
#   - De configuración (credenciales, rol, permisos, pg_hba.conf)
#       -> se aborta con exit 1: reintentar no los corrige y el contenedor debe
#          quedar visiblemente fallido en lugar de reintentar en silencio.
#
# Variables:
#   PRIMARY_HOST            Host del primario en la red de contenedores (por defecto: postgres-primary).
#   PRIMARY_PORT            Puerto del primario (por defecto: 5432).
#   PGPASSWORD              Contraseña del rol "replicator" (la inyecta compose / stack).
#   BASEBACKUP_RETRY_DELAY  Segundos entre reintentos transitorios (por defecto: 3).
set -euo pipefail

PRIMARY_HOST="${PRIMARY_HOST:-postgres-primary}"
PRIMARY_PORT="${PRIMARY_PORT:-5432}"
RETRY_DELAY="${BASEBACKUP_RETRY_DELAY:-3}"

# Mensajes de libpq / PostgreSQL que indican un error de configuración, no de disponibilidad.
CONFIG_ERROR_PATTERN='password authentication failed|no password supplied|no pg_hba\.conf entry|role ".*" does not exist|WAL sender|walsender'

clean_data_dir() {
  rm -rf "${PGDATA:?}"/*
}

bootstrap_from_primary() {
  local err_log
  err_log="$(mktemp)"
  mkdir -p "$PGDATA"
  chmod 700 "$PGDATA"

  echo "Esperando al primario ${PRIMARY_HOST}:${PRIMARY_PORT} y creando copia base..."
  until pg_basebackup -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U replicator \
      -D "$PGDATA" -Fp -Xs -R -P 2>"$err_log"; do
    cat "$err_log" >&2
    clean_data_dir
    if grep -qE "$CONFIG_ERROR_PATTERN" "$err_log"; then
      echo "FATAL: error de configuración al replicar desde ${PRIMARY_HOST}:${PRIMARY_PORT}." >&2
      echo "FATAL: revisa REPLICATION_PASSWORD, el rol 'replicator' y pg_hba.conf del primario. No se reintenta." >&2
      rm -f "$err_log"
      exit 1
    fi
    echo "Copia base desde ${PRIMARY_HOST}:${PRIMARY_PORT} fallida (error transitorio); reintentando en ${RETRY_DELAY} segundos..." >&2
    sleep "$RETRY_DELAY"
  done

  cat "$err_log" >&2
  rm -f "$err_log"
  echo "Copia base completada; iniciando réplica en modo hot standby."
}

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  bootstrap_from_primary
fi
exec postgres -D "$PGDATA" -c hot_standby=on
