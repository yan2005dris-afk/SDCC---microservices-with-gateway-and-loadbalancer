# Plan de Pruebas Experimentales Obligatorias (P01 — P08)

Este documento define la metodología, comandos, resultados esperados y criterios de evaluación técnica para los ocho escenarios obligatorios de la actividad académica.

## Preparación común y recuperación

Desde la raíz del repositorio, prepara `.env` con valores locales ficticios y arranca esperando healthchecks:

```bash
test -e .env || cp .env.example .env
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
docker compose exec -T postgres-primary sh -lc 'psql -U "$POSTGRES_USER" -d inventory_db -c "SELECT product_code, stock FROM inventory_products ORDER BY product_code;"'
docker compose exec -T postgres-primary sh -lc 'psql -U "$POSTGRES_USER" -d postgres -c "SELECT application_name, state, sync_state FROM pg_stat_replication ORDER BY application_name;"'
```

Usa solo el stack local de pruebas. Anota por solicitud el código HTTP y las cabeceras; los objetivos estadísticos no sustituyen los resultados medidos. Tras cada escenario de fallo, restaura el componente (`docker start order-service-1` o `docker start postgres-replica1`) y verifica salud antes del siguiente. Detén el stack con `docker compose stop` si corresponde; no borres volúmenes (`down -v`). Los puertos PostgreSQL dentro de la red Compose son `5432` en los tres contenedores; los puertos publicados por defecto en el host son `5435`, `5433` y `5434` respectivamente.

---

### P01 — Descubrimiento Dinámico
- **Objetivo:** Demostrar que los microservicios se registran automáticamente en Eureka y el Gateway localiza las instancias sin conocer IPs ni puertos fijos.
- **Procedimiento:**
  1. Iniciar el clúster esperando healthchecks con `docker compose up -d --build --wait --wait-timeout 300`.
  2. Consultar el dashboard de Eureka en `http://localhost:8761`.
  3. Consultar la API REST de Eureka: `curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps`.
- **Resultado Esperado:** Visualización de las aplicaciones `API-GATEWAY`, `ORDER-SERVICE` (2 instancias registradas) e `INVENTORY-SERVICE` en estado `UP`.

---

### P02 — Balanceo de Carga HTTP
- **Objetivo:** Medir cómo Spring Cloud Gateway y Spring Cloud LoadBalancer distribuyen al menos 100 solicitudes entre las dos instancias de `order-service`.
- **Procedimiento:**
  - Verificar en Eureka que las dos instancias estén `UP` y comprobar una respuesta HTTP 200 desde cada una a través del Gateway antes de medir. `UP` por sí solo no confirma que la lista del Gateway se haya propagado; si hay 503, registra la ventana de arranque y espera ambas respuestas antes de tomar la muestra.
  - Ejecutar `bash scripts/experiments/test-p02-loadbalance.sh` (100 solicitudes por defecto).
  - Registrar por solicitud número, código HTTP, `X-Instance-Id`, `X-Database-Node` y errores de transporte/cabeceras.
- **Criterio de evaluación:** Publicar todos los intentos y los totales reconciliados. Analizar la distribución observada de instancias; no asumir exactamente 50/50 ni contar errores como respuestas correctas.

---

### P03 — Tolerancia al Fallo de Instancia
- **Objetivo:** Medir disponibilidad y selección de instancia durante la caída de una instancia de microservicio.
- **Procedimiento:**
  1. Detener deliberadamente la Instancia 1: `docker stop --timeout 40 order-service-1`.
  2. Registrar 20 solicitudes a `http://localhost:8080/api/orders`, con estado y `X-Instance-Id` por respuesta.
  3. Restaurar la instancia: `docker start order-service-1`; esperar healthcheck y registro `UP` en Eureka.
- **Criterio de evaluación:** Informar tasa de éxito, códigos e instancia real durante la parada y después de restaurar; cualquier 5xx o error queda contabilizado, no se presenta una expectativa como garantía.

---

### P04 — Comunicación Inter-Servicios Dinámica
- **Objetivo:** Demostrar una transacción de negocio real que involucre dos microservicios comunicándose mediante nombres lógicos registrados en Eureka.
- **Procedimiento:**
  - Enviar `POST /api/orders` con el producto semilla `PROD-001`, cantidad válida y datos ficticios.
  - Repetir con cantidad superior al stock y confirmar la respuesta de rechazo; no reutilizar datos personales.
  - El servicio `order-service` invoca internamente `http://inventory-service/api/inventory/reserve` vía Eureka.
- **Criterio de evaluación:** Registrar estado/cabeceras y el ID del pedido creado; el caso rechazado debe devolver el error de stock esperado y no crear un pedido.

---

### P05 — Enrutamiento de Base de Datos (Lectura / Escritura)
- **Objetivo:** Demostrar que las operaciones de modificación (`INSERT`) se ejecutan en PostgreSQL Primary y las consultas (`SELECT`) se distribuyen entre las réplicas.
- **Procedimiento:**
  1. Ejecutar `POST /api/orders` y verificar que `X-Database-Node` indique `PRIMARY`.
  2. Ejecutar varias consultas `GET /api/orders` y registrar el nodo real en `X-Database-Node`.
  3. Contrastar la replicación con `pg_stat_replication` en el primario.
- **Criterio de evaluación:** La escritura debe apuntar a `PRIMARY`; resumir la distribución de nodos de lectura medida, sin garantizar alternancia determinista por petición.

---

### P06 — Tolerancia a Fallos en Réplica de Lectura
- **Objetivo:** Medir las respuestas y la réplica elegida cuando una réplica no está disponible y la otra permanece saludable.
- **Procedimiento:**
  1. Detener el contenedor correcto: `docker stop --timeout 40 postgres-replica1`.
  2. Esperar la detección del sondeo y enviar consultas `GET /api/orders`, registrando todas las cabeceras y errores.
  3. Restaurarlo al finalizar: `docker start postgres-replica1`.
- **Criterio de evaluación:** Cuando se detecte el fallo, `REPLICA_1` no debe seguir siendo elegible; medir respuestas y cualquier fallback/error. La expectativa no es una garantía de cero errores durante la ventana de detección.

---

### P07 — Consistencia de Lectura Crítica (Read-Your-Writes)
- **Objetivo:** Comprobar si un registro recién creado se puede consultar de inmediato mediante la ruta de consistencia crítica.
- **Procedimiento:**
  1. Crear un pedido con `POST /api/orders` y extraer su `id` de la respuesta.
  2. Inmediatamente invocar `GET /api/orders/{id}` con `X-Consistency: critical`.
- **Criterio de evaluación:** Confirmar que se devuelve el ID recién creado y que la cabecera indica `X-Database-Node: PRIMARY`; no usar un ID fijo ni extender la observación a garantías no medidas.

---

### P08 — Recuperación y Reincorporación de Réplica
- **Objetivo:** Medir la recuperación y observar si una réplica detenida y resincronizada vuelve al pool de lecturas.
- **Procedimiento:**
  1. Reiniciar `postgres-replica1`: `docker start postgres-replica1` y medir el tiempo hasta que Compose muestre `healthy`.
  2. Verificar en el primario que `pg_stat_replication` muestre `streaming`. La condición de aptitud de `order-service` es más estricta: en recuperación, receptor WAL `streaming`, lag conocido ≤5 s por defecto; se requieren dos sondeos `UP` consecutivos (configurables).
  3. Enviar lecturas ordinarias y registrar cuándo vuelve a aparecer `REPLICA_1` en `X-Database-Node`.
- **Criterio de evaluación:** Informar por separado salud Compose, estado WAL, tiempo medido y primer reingreso observado; no asumir que `healthy` equivale a estar ya en rotación.
