# PRD — Product Requirements Document
## Sistema Distribuido E-Commerce: Order Service & Inventory Service

### 1. Visión General del Producto
El sistema representa una plataforma de comercio electrónico distribuida y resiliente, construida sobre Spring Boot y Spring Cloud, cuyo objetivo principal es validar experimentalmente tolerancia a fallos, balanceo dinámico de carga HTTP y persistencia replicada en PostgreSQL mediante replicación física WAL (1 nodo Primary y 2 Read Replicas).

### 2. Objetivos del Negocio y Académicos
1. **Desacoplamiento Operativo:** Separar el dominio transaccional de compras (`order-service`) del dominio de disponibilidad y catálogo (`inventory-service`).
2. **Descubrimiento Dinámico:** Registro y localización automática de servicios en Netflix Eureka Server sin URLs fijas.
3. **Punto de Entrada Unificado y Balanceo:** Spring Cloud Gateway como Reverse Proxy con Spring Cloud LoadBalancer distribuyendo tráfico entre múltiples instancias.
4. **Alta Disponibilidad de Persistencia:** Enrutamiento inteligente de consultas (`AbstractRoutingDataSource`): escrituras y lecturas críticas al Primary, lecturas ordinarias distribuidas equitativamente entre las réplicas tolerando caídas parciales.

---

### 3. Actores y Casos de Uso

| Actor | Caso de Uso | Descripción |
|---|---|---|
| **Cliente HTTP / Frontend** | Crear Pedido | Envía solicitud de compra; el sistema valida inventario y registra el pedido. |
| **Cliente HTTP / Frontend** | Consultar Pedido Creado | Lectura inmediata del pedido recién registrado con consistencia garantizada (*Read-Your-Writes*). |
| **Cliente HTTP / Frontend** | Listar Pedidos | Consulta masiva de pedidos distribuida entre las réplicas de solo lectura. |
| **Administrador / Operador** | Gestión de Inventario | Consulta y actualiza el stock disponible de productos. |
| **Monitor / QA** | Pruebas Experimentales | Provoca caídas de réplicas e instancias para validar recuperación automática. |

---

### 4. Requerimientos Funcionales

#### RF01 — Verificación y Reserva de Stock
- Antes de confirmar cualquier pedido, `order-service` debe comunicarse vía HTTP con `inventory-service` utilizando descubrimiento dinámico por nombre lógico (`lb://inventory-service`).
- Si el stock es insuficiente, la orden se rechaza con código HTTP `400 Bad Request`.

#### RF02 — Creación de Pedido (Escritura en BD)
- Si hay stock, `order-service` persiste la entidad `Order` con estado `CONFIRMED`.
- La operación de escritura se enruta obligatoriamente al **PostgreSQL Primary**.

#### RF03 — Lectura Crítica Inmediata (Read-Your-Writes)
- Tras la creación de un pedido, las consultas directas `GET /api/orders/{id}` dentro del contexto inmediato o marcadas como críticas se dirigen al **Primary** para evitar inconsistencias por retraso de replicación (*replication lag*).

#### RF04 — Listado Ordinario de Pedidos (Balanceo de Lecturas)
- Las consultas `GET /api/orders` deben distribuirse alternadamente (Round-Robin o estrategia ponderada) entre **Replica 1** y **Replica 2**.
- Si una réplica se detiene, las consultas deben ser absorbidas transparentemente por la réplica saludable restante sin arrojar error 500 al cliente.

---

### 5. Requerimientos No Funcionales

- **R01 — Concurrencia:** Capacidad de soportar ráfagas de al menos 100 peticiones concurrentes balanceadas entre 2 instancias del microservicio de pedidos.
- **R02 — Resiliencia:** Recuperación automática ante desconexión de una réplica o caída de una instancia de servicio.
- **R03 — Observabilidad:** Respuestas HTTP y logs estructurados que identifiquen explícitamente:
  - ID de la instancia del microservicio que atendió la petición (`X-Instance-Id`).
  - Nodo de base de datos utilizado (`X-Database-Node`: `PRIMARY`, `REPLICA_1`, `REPLICA_2`).

---

### 6. Matriz de Trazabilidad con Pruebas Experimentales

| Escenario | Requisito Relacionado | Criterio de Aceptación |
|---|---|---|
| **P01: Descubrimiento Dinámico** | Eureka Server | Registro automático de `api-gateway`, `order-service` (2 instancias) e `inventory-service`. |
| **P02: Balanceo HTTP** | Gateway + LoadBalancer | Distribución equitativa de 100 requests entre las 2 instancias de `order-service`. |
| **P03: Fallo de Instancia** | Resiliencia HTTP | Al apagar una instancia de `order-service`, el 100% de las peticiones se atienden en la instancia viva. |
| **P04: Comunicación Inter-Servicios** | Eureka + RestClient | `POST /api/orders` llama a `inventory-service` por nombre lógico antes de persistir. |
| **P05: Enrutamiento de BD** | Persistencia Replicada | Writes van al Primary; Reads van balanceados a Replica 1 y Replica 2. |
| **P06: Fallo de Réplica** | Tolerancia de Persistencia | Al detener Replica 1, las lecturas se redirigen automáticamente a Replica 2 sin error. |
| **P07: Consistencia de Lectura** | Read-Your-Writes | Un registro recién creado es legible inmediatamente consultando el Primary. |
| **P08: Recuperación de Réplica** | Healthcheck & Routing | Al reiniciar la réplica detenida, esta vuelve a ser incluida en el pool de lecturas. |
