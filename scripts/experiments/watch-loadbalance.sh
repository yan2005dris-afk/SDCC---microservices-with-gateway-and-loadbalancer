#!/usr/bin/env bash
# ============================================================================
# VISUALIZADOR EN TIEMPO REAL DE BALANCEO DE CARGA (HTTP & BASE DE DATOS)
# ============================================================================
# Permite ejecutar peticiones GET (balanceo de lectura) o POST (creación/escritura)
# Uso:
#   ./scripts/experiments/watch-loadbalance.sh [URL] [TOTAL_REQUESTS] [METODO]
# Ejemplos:
#   ./scripts/experiments/watch-loadbalance.sh                                # GET 20 pedidos
#   ./scripts/experiments/watch-loadbalance.sh http://localhost:8080/api/orders 50 GET
#   ./scripts/experiments/watch-loadbalance.sh http://localhost:8080/api/orders 5 POST
# ============================================================================
set -uo pipefail

GATEWAY_URL="${1:-http://localhost:8080/api/orders}"
TOTAL_REQUESTS="${2:-20}"
HTTP_METHOD="${3:-GET}"
HTTP_METHOD="$(echo "$HTTP_METHOD" | tr '[:lower:]' '[:upper:]')"

CLR_RESET="\033[0m"
CLR_BOLD="\033[1m"
CLR_BLUE="\033[38;5;39m"
CLR_GREEN="\033[38;5;42m"
CLR_PURPLE="\033[38;5;171m"
CLR_YELLOW="\033[38;5;220m"
CLR_RED="\033[38;5;196m"
CLR_CYAN="\033[38;5;51m"
CLR_GRAY="\033[38;5;244m"
CLR_ORANGE="\033[38;5;208m"

# Payload por defecto para peticiones POST
POST_BODY='{"customerName":"Demo Cliente","customerEmail":"demo@loadbalance.local","productCode":"PROD-001","quantity":1,"unitPrice":1899.99}'

printf "${CLR_BOLD}${CLR_CYAN}================================================================================${CLR_RESET}\n"
printf "${CLR_BOLD}${CLR_CYAN}   DEMO EN VIVO: BALANCEADOR SPRING CLOUD GATEWAY & POSTGRESQL REPLICAS         ${CLR_RESET}\n"
printf "${CLR_BOLD}${CLR_CYAN}================================================================================${CLR_RESET}\n"
printf "Método HTTP    : ${CLR_BOLD}%s${CLR_RESET}\n" "$HTTP_METHOD"
printf "URL Destino    : ${CLR_BOLD}%s${CLR_RESET}\n" "$GATEWAY_URL"
printf "Total Peticiones: ${CLR_BOLD}%s${CLR_RESET}\n" "$TOTAL_REQUESTS"
if [[ "$HTTP_METHOD" == "POST" ]]; then
    printf "Tipo Operación : ${CLR_ORANGE}ESCRITURA (Creación de pedidos en PRIMARY con reserva de stock)${CLR_RESET}\n"
else
    printf "Tipo Operación : ${CLR_CYAN}LECTURA (Balanceo HTTP en Gateway + Balanceo SQL en RÉPLICAS)${CLR_RESET}\n"
fi
printf "\n"

printf "${CLR_BOLD}%-5s | %-6s | %-8s | %-24s | %-14s | %-15s${CLR_RESET}\n" "Req #" "Método" "Estado" "Instancia Servidora" "Nodo BD" "Visual"
printf -- "----------------------------------------------------------------------------------------\n"

count_ord1=0
count_ord2=0
count_rep1=0
count_rep2=0
count_primary=0
count_err=0

for ((i=1; i<=TOTAL_REQUESTS; i++)); do
    # Ejecutar petición según el método especificado
    if [[ "$HTTP_METHOD" == "POST" ]]; then
        resp=$(curl -s -i -X POST -H "Content-Type: application/json" \
            -d "$POST_BODY" \
            --connect-timeout 2 --max-time 5 "$GATEWAY_URL" 2>&1)
    else
        resp=$(curl -s -i -X GET \
            --connect-timeout 2 --max-time 5 "$GATEWAY_URL" 2>&1)
    fi
    
    http_code=$(printf '%s\n' "$resp" | grep -m1 -E "^HTTP/" | awk '{print $2}')
    instance_id=$(printf '%s\n' "$resp" | awk -F': ' 'tolower($1)=="x-instance-id" {gsub(/\r/,""); print $2}')
    db_node=$(printf '%s\n' "$resp" | awk -F': ' 'tolower($1)=="x-database-node" {gsub(/\r/,""); print $2}')

    http_code="${http_code:-ERR}"
    instance_id="${instance_id:-UNKNOWN}"
    db_node="${db_node:-NONE}"

    # Visual tags para el método
    method_badge="${CLR_CYAN}GET${CLR_RESET}"
    if [[ "$HTTP_METHOD" == "POST" ]]; then
        method_badge="${CLR_ORANGE}POST${CLR_RESET}"
    fi

    # Visual tags para la instancia de microservicio
    inst_badge="${CLR_GRAY}${instance_id}${CLR_RESET}"
    if [[ "$instance_id" =~ "order-service-1" || "$instance_id" =~ "8081" ]]; then
        inst_badge="${CLR_GREEN}${instance_id}${CLR_RESET}"
        count_ord1=$((count_ord1 + 1))
        flow_tag="[Instancia 1]"
    elif [[ "$instance_id" =~ "order-service-2" || "$instance_id" =~ "8083" ]]; then
        inst_badge="${CLR_PURPLE}${instance_id}${CLR_RESET}"
        count_ord2=$((count_ord2 + 1))
        flow_tag="[Instancia 2]"
    elif [[ "$instance_id" =~ "inventory" ]]; then
        inst_badge="${CLR_YELLOW}${instance_id}${CLR_RESET}"
        flow_tag="[Inventario]"
    else
        flow_tag="[Desconocido]"
        count_err=$((count_err + 1))
    fi

    # Visual tags para el nodo de Base de Datos
    db_badge="${CLR_GRAY}${db_node}${CLR_RESET}"
    if [[ "$db_node" == "PRIMARY" ]]; then
        db_badge="${CLR_RED}${db_node}${CLR_RESET}"
        count_primary=$((count_primary + 1))
    elif [[ "$db_node" == "REPLICA_1" ]]; then
        db_badge="${CLR_CYAN}${db_node}${CLR_RESET}"
        count_rep1=$((count_rep1 + 1))
    elif [[ "$db_node" == "REPLICA_2" ]]; then
        db_badge="${CLR_BLUE}${db_node}${CLR_RESET}"
        count_rep2=$((count_rep2 + 1))
    fi

    # Visual tags para el código HTTP
    status_badge="${CLR_GREEN}${http_code}${CLR_RESET}"
    if [[ "$http_code" != "200" && "$http_code" != "201" ]]; then
        status_badge="${CLR_RED}${http_code}${CLR_RESET}"
        count_err=$((count_err + 1))
    fi

    printf "%-5s | %-15b | %-17b | %-33b | %-23b | %-15s\n" \
        "$i" "$method_badge" "$status_badge" "$inst_badge" "$db_badge" "$flow_tag"

    sleep 0.15
done

printf -- "----------------------------------------------------------------------------------------\n"
printf "${CLR_BOLD}RESUMEN DE DISTRIBUCIÓN DEL BALANCEADOR (HTTP GATEWAY):${CLR_RESET}\n"
printf "  • ${CLR_GREEN}order-service-1 (:8081)${CLR_RESET} : %s peticiones (%s%%)\n" "$count_ord1" "$(( count_ord1 * 100 / TOTAL_REQUESTS ))"
printf "  • ${CLR_PURPLE}order-service-2 (:8083)${CLR_RESET} : %s peticiones (%s%%)\n" "$count_ord2" "$(( count_ord2 * 100 / TOTAL_REQUESTS ))"
printf "\n${CLR_BOLD}DISTRIBUCIÓN EN BASES DE DATOS (READ-SPLITTING & ESCRITURA):${CLR_RESET}\n"
if (( count_primary > 0 )); then
    printf "  • ${CLR_RED}postgres-primary${CLR_RESET}        : %s operaciones (Escritura/Crítica)\n" "$count_primary"
fi
if (( count_rep1 > 0 )); then
    printf "  • ${CLR_CYAN}postgres-replica1${CLR_RESET}       : %s lecturas\n" "$count_rep1"
fi
if (( count_rep2 > 0 )); then
    printf "  • ${CLR_BLUE}postgres-replica2${CLR_RESET}       : %s lecturas\n" "$count_rep2"
fi
if (( count_err > 0 )); then
    printf "  • ${CLR_RED}Errores / 503 / Caídas${CLR_RESET}  : %s\n" "$count_err"
fi
printf "========================================================================================\n"
