# Plan de Pruebas Experimentales Obligatorias (P01 — P08)

Este documento define la metodología, comandos, resultados esperados y criterios de evaluación técnica para los ocho escenarios obligatorios de la actividad académica.

---

### P01 — Descubrimiento Dinámico
- **Objetivo:** Demostrar que los microservicios se registran automáticamente en Eureka y el Gateway localiza las instancias sin conocer IPs ni puertos fijos.
- **Procedimiento:**
  1. Iniciar el cluster con `docker compose up -d`.
  2. Consultar el dashboard de Eureka en `http://localhost:8761`.
  3. Consultar la API REST de Eureka: `curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps`.
- **Resultado Esperado:** Visualización de las aplicaciones `API-GATEWAY`, `ORDER-SERVICE` (2 instancias registradas) e `INVENTORY-SERVICE` en estado `UP`.

---

### P02 — Balanceo de Carga HTTP
- **Objetivo:** Demostrar que Spring Cloud Gateway y Spring Cloud LoadBalancer distribuyen al menos 100 peticiones concurrentes entre las dos instancias de `order-service`.
- **Procedimiento:**
  - Ejecutar un script de pruebas enviando 100 solicitudes consecutivas a `http://localhost:8080/api/orders`.
  - Recopilar las cabeceras `X-Instance-Id` devueltas en cada respuesta.
- **Resultado Esperado:** Distribución estadística equilibrada (~50% atendidas por la Instancia 1 y ~50% por la Instancia 2).

---

### P03 — Tolerancia al Fallo de Instancia
- **Objetivo:** Demostrar que el sistema continúa operando sin interrupción para los clientes cuando una instancia de un microservicio cae.
- **Procedimiento:**
  1. Detener deliberadamente la Instancia 1 de `order-service`: `docker stop order-service-1`.
  2. Enviar inmediatamente una ráfaga de 20 peticiones a `http://localhost:8080/api/orders`.
- **Resultado Esperado:** El 100% de las solicitudes son respondidas exitosamente con código `200 OK` por la Instancia 2 viva. No hay respuestas `503 Service Unavailable`.

---

### P04 — Comunicación Inter-Servicios Dinámica
- **Objetivo:** Demostrar una transacción de negocio real que involucre dos microservicios comunicándose mediante nombres lógicos registrados en Eureka.
- **Procedimiento:**
  - Enviar solicitud de creación de pedido: `POST /api/orders` con un producto específico.
  - El servicio `order-service` invoca internamente `http://inventory-service/api/inventory/reserve` vía Eureka.
- **Resultado Esperado:** El pedido se crea únicamente si hay stock suficiente en `inventory-service`. En caso de inventario 0, responde `400 Bad Request`.

---

### P05 — Enrutamiento de Base de Datos (Lectura / Escritura)
- **Objetivo:** Demostrar que las operaciones de modificación (`INSERT`) se ejecutan en PostgreSQL Primary y las consultas (`SELECT`) se distribuyen entre las réplicas.
- **Procedimiento:**
  1. Ejecutar `POST /api/orders` y verificar que la cabecera `X-Database-Node` indique `PRIMARY`.
  2. Ejecutar dos consultas `GET /api/orders` consecutivas y verificar que la cabecera indique alternadamente `REPLICA_1` y `REPLICA_2`.
- **Resultado Esperado:** Trazabilidad clara en cabeceras y en los logs de PostgreSQL confirmando la recepción de transacciones en los nodos correspondientes.

---

### P06 — Tolerancia a Fallos en Réplica de Lectura
- **Objetivo:** Demostrar que la indisponibilidad de una de las réplicas no degrada el servicio de consultas si la otra réplica permanece saludable.
- **Procedimiento:**
  1. Detener el contenedor de la réplica 1: `docker stop pg-replica1`.
  2. Enviar consultas `GET /api/orders` consecutivas.
- **Resultado Esperado:** El balanceador detecta la caída de `REPLICA_1` y enruta automáticamente el 100% de las consultas a `REPLICA_2`. Ningún cliente recibe error de conexión.

---

### P07 — Consistencia de Lectura Crítica (Read-Your-Writes)
- **Objetivo:** Garantizar que un registro recién creado pueda consultarse de inmediato sin verse afectado por el retraso de replicación física de PostgreSQL.
- **Procedimiento:**
  1. Crear un pedido con `POST /api/orders`.
  2. Inmediatamente invocar `GET /api/orders/{id}` con la política de consistencia crítica activada.
- **Resultado Esperado:** La lectura se redirige al nodo `PRIMARY` (demostrado en cabecera `X-Database-Node: PRIMARY`), devolviendo el registro sin sufrir posibles desfases de replicación.

---

### P08 — Recuperación y Reincorporación de Réplica
- **Objetivo:** Demostrar que una réplica detenida, una vez reanudada y sincronizada, se reintegra al pool de balanceo de lecturas automáticamente.
- **Procedimiento:**
  1. Reiniciar el contenedor previamente detenido: `docker start pg-replica1`.
  2. Esperar que el healthcheck activo verifique que está saludable y sincronizado.
  3. Ejecutar ráfaga de lecturas `GET /api/orders`.
- **Resultado Esperado:** `REPLICA_1` vuelve a recibir consultas compartiendo la carga con `REPLICA_2`.
