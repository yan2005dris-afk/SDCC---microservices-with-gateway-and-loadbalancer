#!/usr/bin/env bash
# =============================================================================
# SCRIPT DE DESPLIEGUE AUTOMATIZADO EN DOCKER SWARM
# =============================================================================
set -euo pipefail

STACK_NAME="sdcc"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STACK_FILE="${DEPLOY_DIR}/docker-stack.yml"

echo "======================================================================"
echo "          DESPLIEGUE EN DOCKER SWARM - STACK: ${STACK_NAME}           "
echo "======================================================================"

# 1. Verificar si Docker Swarm está activo
if ! docker info --format '{{.Swarm.LocalNodeState}}' | grep -q "active"; then
  echo "==> Docker Swarm no está activo. Inicializando nodo manager..."
  docker swarm init || true
else
  echo "==> Docker Swarm ya se encuentra activo."
fi

# 2. Desplegar el stack
echo "==> Desplegando stack '${STACK_NAME}' desde ${STACK_FILE}..."
docker stack deploy -c "${STACK_FILE}" "${STACK_NAME}"

echo "==> Verificando servicios desplegados:"
docker stack services "${STACK_NAME}"

echo ""
echo "======================================================================"
echo "Comandos útiles para monitorear el cluster:"
echo "  - Ver servicios: docker stack services ${STACK_NAME}"
echo "  - Ver tareas/réplicas: docker stack ps ${STACK_NAME}"
echo "  - Escalar órdenes: docker service scale ${STACK_NAME}_order-service=3"
echo "  - Ver logs: docker service logs -f ${STACK_NAME}_api-gateway"
echo "  - Destruir stack: docker stack rm ${STACK_NAME}"
echo "======================================================================"
