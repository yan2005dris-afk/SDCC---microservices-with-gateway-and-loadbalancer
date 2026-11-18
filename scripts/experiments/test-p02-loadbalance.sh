#!/usr/bin/env bash
# ==============================================================================
# SCRIPT DE VALIDACIÓN EXPERIMENTAL P02: BALANCEO DE 100 SOLICITUDES HTTP
# ==============================================================================
set -euo pipefail

GATEWAY_URL="http://localhost:8080/api/orders"
TOTAL_REQUESTS=100

echo "===================================================================="
echo "Iniciando prueba P02: Enviando $TOTAL_REQUESTS solicitudes a $GATEWAY_URL..."
echo "===================================================================="

ORDER_1_COUNT=0
ORDER_2_COUNT=0
REPLICA_1_COUNT=0
REPLICA_2_COUNT=0
PRIMARY_COUNT=0

for i in $(seq 1 $TOTAL_REQUESTS); do
    RESPONSE_HEADERS=$(curl -s -i "$GATEWAY_URL")

    # Extraer cabecera X-Instance-Id
    INSTANCE_ID=$(echo "$RESPONSE_HEADERS" | grep -i "X-Instance-Id:" | awk '{print $2}' | tr -d '\r')
    # Extraer cabecera X-Database-Node
    DB_NODE=$(echo "$RESPONSE_HEADERS" | grep -i "X-Database-Node:" | awk '{print $2}' | tr -d '\r')

    if [[ "$INSTANCE_ID" == *"order-service-1"* ]] || [[ "$INSTANCE_ID" == *":8081"* ]]; then
        ORDER_1_COUNT=$((ORDER_1_COUNT + 1))
    elif [[ "$INSTANCE_ID" == *"order-service-2"* ]] || [[ "$INSTANCE_ID" == *":8083"* ]]; then
        ORDER_2_COUNT=$((ORDER_2_COUNT + 1))
    fi

    if [[ "$DB_NODE" == "REPLICA_1" ]]; then
        REPLICA_1_COUNT=$((REPLICA_1_COUNT + 1))
    elif [[ "$DB_NODE" == "REPLICA_2" ]]; then
        REPLICA_2_COUNT=$((REPLICA_2_COUNT + 1))
    elif [[ "$DB_NODE" == "PRIMARY" ]]; then
        PRIMARY_COUNT=$((PRIMARY_COUNT + 1))
    fi

    if (( i % 20 == 0 )); then
        echo "Completadas $i/$TOTAL_REQUESTS peticiones..."
    fi
done

echo ""
echo "===================================================================="
echo "RESULTADOS DEL BALANCEO HTTP (Spring Cloud LoadBalancer):"
echo "--------------------------------------------------------------------"
echo "order-service-1 (Instancia 1): $ORDER_1_COUNT peticiones (~$(( ORDER_1_COUNT * 100 / TOTAL_REQUESTS ))%)"
echo "order-service-2 (Instancia 2): $ORDER_2_COUNT peticiones (~$(( ORDER_2_COUNT * 100 / TOTAL_REQUESTS ))%)"
echo ""
echo "RESULTADOS DEL BALANCEO DE BASE DE DATOS (AbstractRoutingDataSource):"
echo "--------------------------------------------------------------------"
echo "PostgreSQL Replica 1: $REPLICA_1_COUNT peticiones (~$(( REPLICA_1_COUNT * 100 / TOTAL_REQUESTS ))%)"
echo "PostgreSQL Replica 2: $REPLICA_2_COUNT peticiones (~$(( REPLICA_2_COUNT * 100 / TOTAL_REQUESTS ))%)"
echo "PostgreSQL Primary:   $PRIMARY_COUNT peticiones"
echo "===================================================================="
