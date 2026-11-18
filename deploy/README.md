# Despliegue en Docker Swarm (Modo Swarm)

Esta carpeta contiene la configuración necesaria para desplegar toda la arquitectura distribuida (cluster PostgreSQL con replicación física WAL + Microservicios Spring Boot con Eureka y Gateway) sobre un cluster orquestado con **Docker Swarm**.

---

## 🏗️ Arquitectura en Swarm

A diferencia del modo Compose standalone, en Docker Swarm:
1. **Red Overlay Multi-host (`sdcc-swarm-network`)**: Permite la comunicación segura y enrutada entre contenedores en uno o múltiples nodos del swarm.
2. **Ingress Routing Mesh**: Docker Swarm enruta automáticamente el tráfico entrante a los puertos publicados hacia cualquier nodo del cluster que esté ejecutando réplicas del servicio.
3. **Escalado Declarativo de Microservicios**: `order-service` está configurado con `replicas: 2`, y puede escalarse dinámicamente en caliente con `docker service scale`.
4. **Configs Nativas de Swarm**: Los scripts de inicialización de la replicación de PostgreSQL se inyectan como `docker configs` inmutables.
5. **Políticas de Reinicio y Cuotas de Recursos**: Cada contenedor cuenta con `restart_policy` y límites/reservas declarativas de CPU y memoria.

---

## 🚀 Pasos para Desplegar

### 1. Construir las imágenes Docker
Antes del primer despliegue en Swarm, construye las imágenes locales:
```bash
chmod +x deploy/build-images.sh deploy/deploy-swarm.sh
./deploy/build-images.sh
```

### 2. Inicializar Swarm y Desplegar el Stack
Ejecuta el script automatizado:
```bash
./deploy/deploy-swarm.sh
```

O hazlo manualmente:
```bash
# 1. Iniciar Swarm si aún no está activo
docker swarm init

# 2. Desplegar el stack
docker stack deploy -c deploy/docker-stack.yml sdcc
```

---

## 📊 Monitoreo y Operación

### Ver estado de los servicios:
```bash
docker stack services sdcc
```

### Ver tareas (réplicas y en qué nodos corren):
```bash
docker stack ps sdcc
```

### Ver logs en tiempo real:
```bash
docker service logs -f sdcc_api-gateway
docker service logs -f sdcc_order-service
docker service logs -f sdcc_postgres-primary
```

### Escalar dinámicamente réplicas de `order-service`:
```bash
docker service scale sdcc_order-service=3
```

### Eliminar el stack de Swarm:
```bash
docker stack rm sdcc
```
