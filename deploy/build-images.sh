#!/usr/bin/env bash
# =============================================================================
# SCRIPT DE CONSTRUCCIÓN DE IMÁGENES DOCKER PARA SWARM
# =============================================================================
set -euo pipefail

TAG="${1:-latest}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Construyendo imágenes Docker para despliegue en Swarm con TAG: ${TAG}"

echo "--> [1/4] Eureka Server..."
docker build -t "sdcc/eureka-server:${TAG}" -f "${ROOT_DIR}/src/eureka-server/Dockerfile" "${ROOT_DIR}/src"

echo "--> [2/4] API Gateway..."
docker build -t "sdcc/api-gateway:${TAG}" -f "${ROOT_DIR}/src/api-gateway/Dockerfile" "${ROOT_DIR}/src"

echo "--> [3/4] Order Service..."
docker build -t "sdcc/order-service:${TAG}" -f "${ROOT_DIR}/src/order-service/Dockerfile" "${ROOT_DIR}/src"

echo "--> [4/4] Inventory Service..."
docker build -t "sdcc/inventory-service:${TAG}" -f "${ROOT_DIR}/src/inventory-service/Dockerfile" "${ROOT_DIR}/src"

echo "==> Todas las imágenes fueron construidas con éxito."
echo "Ahora puedes desplegar el stack en Swarm ejecutando:"
echo "  ./deploy/deploy-swarm.sh"
