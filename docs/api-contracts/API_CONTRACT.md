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
    "productCode": "PROD-001",
    "quantity": 2,
    "unitPrice": 49.99
  }
  ```
- **Response `201 Created`:**
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
  - `X-Instance-Id: order-service-instance-1:8081`
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

### 3.1 Verificar y Reservar Stock (Invocado internamente por `order-service`)
- **Método:** `POST /api/inventory/reserve`
- **Request Body:**
  ```json
  {
    "productCode": "PROD-001",
    "quantity": 2
  }
  ```
- **Response `200 OK`:**
  ```json
  {
    "productCode": "PROD-001",
    "available": true,
    "remainingStock": 18
  }
  ```
- **Response `400 Bad Request`:**
  ```json
  {
    "productCode": "PROD-001",
    "available": false,
    "message": "Stock insuficiente para el producto PROD-001"
  }
  ```

---

### 3.2 Consultar Stock de un Producto
- **Método:** `GET /api/inventory/{productCode}`
- **Response `200 OK`:**
  ```json
  {
    "productCode": "PROD-001",
    "name": "Laptop Pro 16",
    "stock": 18,
    "unitPrice": 49.99
  }
  ```
