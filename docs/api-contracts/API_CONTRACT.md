# API Contracts — Especificación de Endpoints y Cabeceras

Todas las peticiones públicas ingresan a través de **Spring Cloud Gateway** en el puerto `8080`.

---

## 1. Cabeceras Especiales de Observabilidad (Presentes en las respuestas)

Para auditar experimentalmente las pruebas P01 a P08, todos los servicios devuelven las siguientes cabeceras HTTP:

| Cabecera | Ejemplo de Valor | Descripción |
|---|---|---|
| `X-Instance-Id` | `order-service-instance-1:8081` | Identificador de la instancia del microservicio que procesó la solicitud. |
| `X-Database-Node` | `PRIMARY`, `REPLICA_1`, `REPLICA_2` | Nodo de la base de datos PostgreSQL desde el cual se leyó o escribió. |
| `X-Replication-Lag-Ms` | `2` | Muestra el retraso aproximado de replicación (si aplica). |

---

## 2. Microservicio de Pedidos (`order-service`)

Prefijo en Gateway: `/api/orders`

### 2.1 Crear Pedido
- **Método:** `POST /api/orders`
- **Enrutamiento BD:** `PRIMARY`
- **Interacción Externa:** Llama a `inventory-service` para verificar y descontar stock.
- **Request Body:**
  ```json
  {
    "customerName": "Juan Perez",
    "customerEmail": "juan@example.invalid",
    "productCode": "PROD-001",
    "quantity": 2
  }
  ```
  El precio no se acepta del cliente: lo fija `inventory-service` al reservar. Un `unitPrice`
  enviado por clientes antiguos se ignora. Cabecera opcional `Idempotency-Key`: un reintento con
  la misma clave devuelve `200` con el pedido ya creado, sin reservar stock otra vez.
- **Response `201 Created`:**
  ```json
  {
    "id": 101,
    "orderNumber": "ORD-...",
    "customerName": "Juan Perez",
    "customerEmail": "juan@example.invalid",
    "totalAmount": 3799.98,
    "status": "CONFIRMED",
    "createdAt": "2026-11-18T16:00:00Z",
    "items": [
      {
        "id": 1,
        "productCode": "PROD-001",
        "productName": "Laptop Dell XPS 15",
        "quantity": 2,
        "unitPrice": 1899.99,
        "subtotal": 3799.98
      }
    ]
  }
  ```
- **Headers devueltos:**
  - `X-Instance-Id: order-service-1:8081`
  - `X-Database-Node: PRIMARY`

---

### 2.2 Consultar Pedido por ID (Lectura Crítica Inmediata — P07)
- **Método:** `GET /api/orders/{id}`
- **Cabecera Opcional:** `X-Consistency: critical` (fuerza lectura en Primary si se requiere garantizar lectura tras escritura)
- **Enrutamiento BD:** `PRIMARY` (o réplica según cabecera)
- **Response `200 OK`:**
  ```json
  {
    "id": 101,
    "customerName": "Juan Perez",
    "productCode": "PROD-001",
    "quantity": 2,
    "totalPrice": 99.98,
    "status": "CONFIRMED",
    "createdAt": "2026-11-18T16:00:00Z"
  }
  ```
- **Headers devueltos:**
  - `X-Instance-Id: order-service-instance-2:8083`
  - `X-Database-Node: PRIMARY`

---

### 2.3 Listar Todos los Pedidos (Lectura Balanceada — P02, P05, P06, P08)
- **Método:** `GET /api/orders`
- **Enrutamiento BD:** Balanceado Round-Robin entre `REPLICA_1` y `REPLICA_2`.
- **Response `200 OK`:**
  ```json
  [
    {
      "id": 101,
      "customerName": "Juan Perez",
      "productCode": "PROD-001",
      "quantity": 2,
      "totalPrice": 99.98,
      "status": "CONFIRMED"
    }
  ]
  ```
- **Headers devueltos (Petición 1):**
  - `X-Instance-Id: order-service-instance-1:8081`
  - `X-Database-Node: REPLICA_1`
- **Headers devueltos (Petición 2):**
  - `X-Instance-Id: order-service-instance-2:8083`
  - `X-Database-Node: REPLICA_2`

---

## 3. Microservicio de Inventario (`inventory-service`)

Prefijo en Gateway: `/api/inventory`

Todos los errores de este microservicio responden `Content-Type: application/problem+json` (RFC 7807),
salvo el rechazo de stock insuficiente (ver **3.5**), que conserva su cuerpo propio por compatibilidad
con `order-service`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "quantity debe ser > 0",
  "instance": "/api/inventory/reserve"
}
```

Las respuestas 2xx incluyen la cabecera `X-Instance-Id` con la instancia que atendió la petición.

### 3.1 Verificar y Reservar Stock (Invocado internamente por `order-service`)
- **Método:** `POST /api/inventory/reserve`
- **Request Body:**
  ```json
  {
    "orderNumber": "ORD-001",
    "productCode": "PROD-001",
    "quantity": 2
  }
  ```
  `orderNumber` es opcional. Cuando se envía, la idempotencia se keyea por
  `(orderNumber, productCode)`: una petición repetida responde `200` **sin descontar stock dos veces**.
  La garantía real es: lock de fila `FOR UPDATE` del producto → inserción del movimiento con
  `ON CONFLICT DO NOTHING` → índice único parcial `(reference_order_number, movement_type, product_code)`.
- **Response `200 OK`:**
  ```json
  {
    "productCode": "PROD-001",
    "available": true,
    "remainingStock": 18,
    "unitPrice": 1899.99,
    "productName": "Laptop Dell XPS 15",
    "message": "Reserva confirmada"
  }
  ```
  `remainingStock` es el stock **después** de aplicar esta reserva (no el stock en el instante de la respuesta).
- **Response `400 Bad Request` — validación:** `application/problem+json` con `detail` =
  `"quantity debe ser > 0"`, `"productCode no puede estar vacío"`, `"quantity es requerido"`, etc.
- **Response `400 Bad Request` — stock insuficiente:** cuerpo propio, ver **3.5**.
- **Response `404 Not Found`:** `application/problem+json` con `detail` = `"Producto no encontrado: PROD-001"`.

---

### 3.2 Liberar Stock
- **Método:** `POST /api/inventory/release`
- **Request Body:**
  ```json
  {
    "orderNumber": "ORD-001"
  }
  ```
- **Response `200 OK`** (incluye las líneas liberadas; las claves de la primera línea se mantienen
  por compatibilidad con clientes que esperaban una sola línea):
  ```json
  {
    "productCode": "PROD-001",
    "quantity": 2,
    "message": "Stock liberado exitosamente",
    "orderNumber": "ORD-001",
    "releases": [
      {
        "productCode": "PROD-001",
        "quantity": 2,
        "message": "Stock liberado exitosamente"
      }
    ]
  }
  ```
- **Idempotencia:** repetir el mismo `orderNumber` responde `200` y **no** vuelve a sumar stock.
  La garantía es: lock de fila del producto, inserción del movimiento con `ON CONFLICT DO NOTHING`,
  índice único parcial `(reference_order_number, movement_type, product_code)`.
- **Response `400 Bad Request` — validación:** `application/problem+json` con `detail` =
  `"orderNumber no puede estar vacío"`.
- **Response `404 Not Found`:** `application/problem+json` con `detail` = `"Reserva no encontrada: ORD-999"`.
- **Response `409 Conflict`:** la liberación excedería el stock reservado del producto.
  ```json
  {
    "type": "about:blank",
    "title": "Conflict",
    "status": 409,
    "detail": "No hay stock reservado suficiente para liberar PROD-001",
    "instance": "/api/inventory/release"
  }
  ```

---

### 3.3 Consultar Stock de un Producto
- **Método:** `GET /api/inventory/{productCode}`
- **Response `200 OK`** (entidad de producto):
  ```json
  {
    "id": 1,
    "productCode": "PROD-001",
    "name": "Laptop Pro 16",
    "description": "Laptop de 16 pulgadas",
    "stock": 18,
    "reservedStock": 2,
    "unitPrice": 49.99,
    "version": 3,
    "createdAt": "2026-01-01T10:00:00Z",
    "updatedAt": "2026-01-01T10:05:00Z"
  }
  ```
  `stock` es el stock físico disponible y `reservedStock` la cantidad ya reservada. El campo
  `remainingStock` existe **únicamente** en las respuestas de reserva (`/reserve`), no en `/stock`.
- **Response `404 Not Found`:** `application/problem+json` con `detail` = `"Producto no encontrado: PROD-999"`.

---

### 3.4 Tabla de errores de `inventory-service`

| Situación | HTTP | `Content-Type` | `detail` |
|---|---|---|---|
| Body ausente, tipo o JSON inválido | `400` | `application/problem+json` | `Cuerpo de solicitud inválido` |
| Validación de negocio (cantidad, código, `orderNumber`) | `400` | `application/problem+json` | `quantity debe ser > 0`, `productCode no puede estar vacío`, `orderNumber no puede estar vacío`, … |
| Producto inexistente | `404` | `application/problem+json` | `Producto no encontrado: <code>` |
| Reserva inexistente al liberar | `404` | `application/problem+json` | `Reserva no encontrada: <orderNumber>` |
| Ruta sin mapping | `404` | `application/problem+json` | detalle por defecto de Spring |
| Método no permitido | `405` | `application/problem+json` | `Method 'PUT' is not supported.` |
| `Content-Type` no soportado | `415` | `application/problem+json` | `Content-Type 'text/plain;charset=UTF-8' is not supported.` |
| Liberación por encima del stock reservado | `409` | `application/problem+json` | `No hay stock reservado suficiente para liberar <code>` |
| Error no controlado | `500` | `application/problem+json` | `Error interno del servidor` (el detalle técnico va al log, nunca al body) |

---

### 3.5 Excepción: rechazo de stock insuficiente (`400`)

Este es el **único** error de `inventory-service` que no usa `application/problem+json`. Su cuerpo se
mantiene como JSON plano porque `order-service` lo deserializa en `InventoryReserveResponse`
(campos `productCode`, `available`, `remainingStock`, `error`):

```json
{
  "productCode": "PROD-001",
  "available": false,
  "remainingStock": 2,
  "message": "Stock insuficiente. Disponible: 2"
}
```
