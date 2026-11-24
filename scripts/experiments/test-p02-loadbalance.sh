#!/usr/bin/env bash
# ============================================================================
# SCRIPT DE VALIDACIÓN EXPERIMENTAL P02: MEDICIÓN POR SOLICITUD
# ============================================================================
set -uo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080/api/orders}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-100}"

if [[ ! "$TOTAL_REQUESTS" =~ ^[0-9]+$ ]] || (( TOTAL_REQUESTS < 100 )); then
    printf 'TOTAL_REQUESTS debe ser un entero >= 100 (recibido: %s)\n' "$TOTAL_REQUESTS" >&2
    exit 2
fi

declare -A HTTP_COUNTS=()
declare -A INSTANCE_COUNTS=()
declare -A NODE_COUNTS=()
attempts=0
http_responses=0
requests_without_response=0
transport_errors=0
header_errors=0
http_errors=0

printf 'P02 URL: %s\nSolicitudes: %s\n' "$GATEWAY_URL" "$TOTAL_REQUESTS"
printf 'request\thttp_status\tinstance_id\tdatabase_node\terror\n'

for ((request=1; request<=TOTAL_REQUESTS; request++)); do
    curl_exit=0
    response=$(curl --silent --show-error --dump-header - --output /dev/null \
        --connect-timeout 3 --max-time 10 --write-out $'\n%{http_code}' \
        "$GATEWAY_URL" 2>&1) || curl_exit=$?
    http_status="$(printf '%s\n' "$response" | tail -n 1)"
    headers="${response%$'\n'*}"

    if [[ ! "$http_status" =~ ^[0-9]{3}$ ]]; then
        http_status=000
    fi

    instance_id=$(printf '%s\n' "$headers" | awk 'tolower($1) == "x-instance-id:" { $1=""; sub(/^[[:space:]]+/, ""); gsub(/\r/, ""); value=$0 } END { print value }')
    database_node=$(printf '%s\n' "$headers" | awk 'tolower($1) == "x-database-node:" { $1=""; sub(/^[[:space:]]+/, ""); gsub(/\r/, ""); value=$0 } END { print value }')
    error=""

    attempts=$((attempts + 1))
    HTTP_COUNTS["$http_status"]=$((${HTTP_COUNTS["$http_status"]:-0} + 1))
    if (( curl_exit != 0 )); then
        transport_errors=$((transport_errors + 1))
        detail=$(printf '%s\n' "$response" | sed -n '/^curl: /p' | tr '\t\r\n' '   ' | sed -E 's/[[:space:]]+/ /g')
        error="transport_exit=$curl_exit:${detail:-transfer-error}"
    fi
    if [[ "$http_status" == 000 ]]; then
        requests_without_response=$((requests_without_response + 1))
        if [[ -z "$error" ]]; then
            error="no_http_response"
        fi
    else
        http_responses=$((http_responses + 1))
        if [[ "$http_status" != 200 ]]; then
            http_errors=$((http_errors + 1))
            error="http_not_200=$http_status"
        fi
        missing_headers=""
        if [[ -z "$instance_id" ]]; then
            missing_headers="X-Instance-Id"
        fi
        if [[ -z "$database_node" ]]; then
            if [[ -n "$missing_headers" ]]; then
                missing_headers+=";"
            fi
            missing_headers+="X-Database-Node"
        fi
        if [[ -n "$missing_headers" ]]; then
            header_errors=$((header_errors + 1))
            if [[ -n "$error" ]]; then
                error+=";"
            fi
            error+="missing_headers=$missing_headers"
        fi
    fi

    INSTANCE_COUNTS["${instance_id:-UNKNOWN}"]=$((${INSTANCE_COUNTS["${instance_id:-UNKNOWN}"]:-0} + 1))
    NODE_COUNTS["${database_node:-UNKNOWN}"]=$((${NODE_COUNTS["${database_node:-UNKNOWN}"]:-0} + 1))
    printf '%s\t%s\t%s\t%s\t%s\n' "$request" "$http_status" "${instance_id:-MISSING}" "${database_node:-MISSING}" "${error:-none}"
done

reconciled=$((http_responses + requests_without_response))
printf '\nResumen\n'
printf 'attempts=%s\nhttp_responses=%s\nrequests_without_response=%s\ntransport_errors=%s\nhttp_errors=%s\nheader_errors=%s\n' \
    "$attempts" "$http_responses" "$requests_without_response" "$transport_errors" "$http_errors" "$header_errors"
printf 'request_reconciliation=%s/%s\n' "$reconciled" "$attempts"
printf 'HTTP statuses:\n'
for status in "${!HTTP_COUNTS[@]}"; do
    printf '  %s=%s\n' "$status" "${HTTP_COUNTS[$status]}"
done | sort
printf 'Instances:\n'
for instance in "${!INSTANCE_COUNTS[@]}"; do
    printf '  %s=%s\n' "$instance" "${INSTANCE_COUNTS[$instance]}"
done | sort
printf 'Database nodes:\n'
for node in "${!NODE_COUNTS[@]}"; do
    printf '  %s=%s\n' "$node" "${NODE_COUNTS[$node]}"
done | sort

if (( attempts != TOTAL_REQUESTS || reconciled != attempts || requests_without_response > 0 || transport_errors > 0 || http_errors > 0 || header_errors > 0 )); then
    exit 1
fi
