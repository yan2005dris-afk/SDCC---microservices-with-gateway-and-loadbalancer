# Guía de Integración — Spring Cloud LoadBalancer

Este documento define los contratos técnicos para que los integrantes responsables de **Spring Cloud Gateway**, **Microservicio 1 (`order-service`)** y **Microservicio 2 (`inventory-service`)** se conecten correctamente con el módulo **Spring Cloud LoadBalancer**.

---

## 1. Conexión con Spring Cloud Gateway (Integrante 1)

### 1.1 Esquema de Rutas Obligatorio (`lb://`)
Para que `ReactiveLoadBalancerClientFilter` delegue el enrutamiento al balanceador configurado en `GatewayLoadBalancerConfig`, toda ruta dinámica en `src/api-gateway/src/main/resources/application.yml` **debe usar el esquema `lb://<service-id>`** coincidente con `spring.application.name` del microservicio destino:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service-route
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
        - id: inventory-service-route
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**
```

### 1.2 Comportamiento Provisto por LoadBalancer
- **Algoritmo:** `RoundRobinLoadBalancer` explícito en `ServiceLoadBalancerConfiguration`, alternando peticiones equitativamente entre instancias vivas (`P02`).
- **Health Checks Activos:** `ServiceInstanceListSupplier.builder().withDiscoveryClient().withHealthChecks().withCaching()` consulta periódicamente el endpoint de salud para retirar instancias caídas sin depender solo de la expiración de Eureka (`P03`).
- **Propiedades Clave (`application.yml`):**
  ```yaml
  spring:
    cloud:
      loadbalancer:
        health-check:
          initial-delay: 1s
          interval: 5s
          path:
            default: /actuator/health
        cache:
          enabled: true
          ttl: 5s

  sdcc:
    loadbalancer:
      health-check-interval: 5s
      health-check-path: /actuator/health
      cache-ttl: 5s
  ```
- **Telemetría:** `LoadBalancerTelemetryLifecycle` emite logs estructurados `[LOADBALANCER SELECT]` y `[LOADBALANCER COMPLETE]` por cada selección sin interferir con `LoggingAndTracingGlobalFilter` ni con Resilience4j `CircuitBreaker`.

---

## 2. Conexión con Microservicio 1: `order-service` (Integrante 3)

### 2.1 Requisitos de Registro para Ser Balanceado por el Gateway
Para que múltiples réplicas de `order-service` sean descubiertas y balanceadas en 50/50 desde `api-gateway`:
1. **`spring.application.name: order-service`** idéntico en todas las instancias.
2. **`eureka.instance.instance-id`** único por réplica (por ejemplo, `${INSTANCE_ID:${spring.application.name}:${server.port}}`).
3. **`eureka.instance.prefer-ip-address: true`** para evitar resolución ambigua de hostnames en contenedores Docker Bridge/Swarm.
4. **Endpoint `/actuator/health` expuesto** en `management.endpoints.web.exposure.include` (consumido por el health-check de LoadBalancer).
5. **Cabecera `X-Instance-Id`** en las respuestas HTTP para auditar qué instancia atendió cada petición en `P02` y `P03`.

### 2.2 Uso del Cliente Balanceado hacia `inventory-service` (`P04`)
El controlador y los casos de uso de `order-service` **no deben instanciar `RestClient` ni usar `Map<String, Object>` directamente**. Deben inyectar la interfaz `InventoryStockClient`:

```java
InventoryReserveResponse response = inventoryStockClient.reserveStock(
    new InventoryReserveRequest(request.productCode(), request.quantity()));
```

- **Contratos fuertemente tipados:**
  - Entrada HTTP del controlador: `CreateOrderRequest` (`customerName`, `customerEmail`, `productCode`, `quantity`, `unitPrice`).
  - Salida RPC hacia inventario: `InventoryReserveRequest` (`productCode`, `quantity`).
  - Respuesta RPC desde inventario: `InventoryReserveResponse` (`productCode`, `available`, `remainingStock`, `error`).
- **Configuración:** `OrderLoadBalancerConfig` provee el `@LoadBalanced RestClient.Builder`, registra `RoundRobinLoadBalancer` y activa `OrderLoadBalancerLifecycle`. La URL base por defecto `http://inventory-service` puede sobreescribirse en pruebas mediante la propiedad `sdcc.loadbalancer.inventory-base-url`.

---

## 3. Conexión con Microservicio 2: `inventory-service` (Integrante 4)

### 3.1 Requisitos de Descubrimiento y Salud
Para integrarse tanto con `api-gateway` (`lb://inventory-service`) como con las llamadas RPC de `order-service`:
1. **`spring.application.name: inventory-service`** en `src/inventory-service/src/main/resources/application.yml`.
2. **`eureka.instance.instance-id`** único por instancia (`${INSTANCE_ID:${spring.application.name}:${server.port}}`) y `prefer-ip-address: true`.
3. **`/actuator/health` habilitado** y retornando HTTP `200 OK` cuando la base de datos `inventory_db` esté operativa.

### 3.2 Contrato Exacto de `POST /api/inventory/reserve`
`LoadBalancedInventoryStockClient` envía y deserializa JSON fuertemente tipado contra `http://inventory-service/api/inventory/reserve`:

- **Request JSON esperado por `inventory-service`:**
  ```json
  {
    "productCode": "PROD-001",
    "quantity": 2
  }
  ```
- **Response JSON `200 OK` esperada por `order-service`:**
  ```json
  {
    "productCode": "PROD-001",
    "available": true,
    "remainingStock": 48
  }
  ```
- **Response JSON `400 Bad Request` (stock insuficiente o error de negocio):**
  ```json
  {
    "productCode": "PROD-001",
    "available": false,
    "error": "Stock insuficiente",
    "currentStock": 0
  }
  ```
  > Nota: Ante códigos 4xx/5xx, `RestClient` lanza `RestClientResponseException` (capturada de forma controlada por `OrderController`). Si `inventory-service` devuelve `200 OK` con `available: false`, `InventoryReserveResponse#isAvailable()` también rechazará el pedido de forma segura.

### 3.3 Escalado Futuro de `inventory-service`
Si el Integrante 4 levanta una segunda instancia de `inventory-service` (por ejemplo, en puerto `8084` o escalando réplicas en `deploy/docker-stack.yml`), **no requiere cambios en `order-service` ni en `api-gateway`**: Eureka registrará ambas instancias y `RoundRobinLoadBalancer` distribuirá automáticamente tanto `/api/inventory/**` como las llamadas RPC de `InventoryStockClient`.
