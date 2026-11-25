# Errata de verificación — B4-08 (rework de B4-05)

**Fecha:** 2026-11-24 09:10
**Autor:** verificación `03-verify-odd` (Mavis)
**Aplica a:** `odd/archive/B4-08-inventory-idempotence-concurrency.md`
**Motivo:** la ODD B4-08 se escribió **antes** de la re-verificación con probes contra el esquema real de
Postgres 15. Tres puntos de la ODD están mal planteados y, si se implementan tal cual, **el defecto se
reproduce**.

> Esta errata **no reemplaza** B4-08: la corrige. Aplíquela junto con la ODD.

---

## E1 — La idempotencia NO es "per orderNumber": es **per orderNumber + productCode**

**Lo que dice B4-08 (S1):**

> 1. Buscar `StockMovement(reference_order_number=orderNumber, type=RESERVATION)` **antes** de descontar.
> 2. Si existe → devolver 200 con el movimiento anterior.
> ...
> POST /reserve ORD-001 PROD-001 qty=5 → 200 OK, stock 45→45 (sin cambiar)

**El problema:** el criterio de aceptación solo prueba el **mismo producto** dos veces. La solución propuesta
(buscar por `orderNumber` + `RESERVATION` y devolver 200 si existe) es **exactamente el bug actual**: el
`orderNumber` es de una **orden**, que puede tener **varias líneas con productos distintos**.

**Evidencia (probe real, HEAD):**

```
P1 after reserve A: PROD-A=45/5 PROD-B=30/0
P1 second reserve (PROD-B, same orderNumber) -> available=true remainingStock=30 | PROD-A=45/5 PROD-B=30/0
```

La 2ª reserva (PROD-B, mismo `orderNumber`) responde **200 `available=true`** y **PROD-B sigue `30/0`**: no se
reservó nada, pero el cliente (order-service) cree que sí. Resultado: **sobreventa silenciosa** — la orden
confirma el stock de la 2ª línea sin que el inventario lo descuente.

**Corrección obligatoria:**

1. La búsqueda de idempotencia debe ser por **`(reference_order_number, movement_type, product_code)`**.
   Si la clave real fuera sólo `orderNumber`, el índice único `uq_stock_movement_ref_type` tendría que incluir
   `product_code` → **el índice actual no permite 2 RESERVATION con el mismo `orderNumber` pero distinto
   producto** → eso es exactamente lo que una orden multi-línea necesita.
2. **El `index unique` debe cambiar** a `(reference_order_number, movement_type, product_code)` (o el
   `product_code` debe salir de la clave y la idempotencia 应用arse por `(order, producto)`).
   ⚠️ Esto es un **cambio de esquema** → hay que decidir y documentar: ¿una orden puede tener 2 líneas del
   mismo producto? Si no, la clave `(order, product)` no aporta; si sí, el índice actual **impide** la
   multi-línea.
3. **Criterio de aceptación nuevo (obligatorio):**

```
POST /reserve ORD-001 PROD-A qty=5 → stock A 50→45, reserved A 0→5, movimiento RESERVATION
POST /reserve ORD-001 PROD-A qty=5 → 200, stock A 45→45 (idempotente, MISMO producto)
POST /reserve ORD-001 PROD-B qty=3 → 200, stock B 30→27, reserved B 0→3, movimiento RESERVATION(ORD-001, PROD-B)
   ^^^ este caso es el que hoy FALLA (devuelve 200 sin reservar)
```

---

## E2 — El `CHECK (reserved_stock >= 0)` **ya existe**: la premisa de S4 es falsa

**Lo que dice B4-08 (S4):**

> Causa: no hay constraint `CHECK (reserved_stock >= 0)` en tabla, o no funciona en transacciones simultáneas.
> **Solución:** Verificar constraint en `02-init-schemas.sql`: `CHECK (reserved_stock >= 0)`.

**Realidad:** el constraint **ya está** en `docker/postgres/02-init-schemas.sql:52`:

```sql
reserved_stock INT NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
```

La tarea G ("verificar constraint") lo confirma, pero la premisa de S4 parte de que falta.

**Y el síntoma real es otro (medido):**

| Escenario | Resultado medido |
|---|---|
| Con el CHECK de Postgres (esquema real) | `DataIntegrityViolationException` → la fila queda `45/0` (rollback) → la API responde **500**, no un 400 controlado |
| Sin el CHECK (BD vieja, o si alguien lo quita) | `updated=1`, `reserved_stock = -4`, **stock inflado 45→49** → **sobreventa silenciosa** |

**Corrección obligatoria:**

1. S4 debe partir de "**el constraint existe pero la query no valida el invariante**".
2. El fix correcto **no** es "agregar el constraint" sino **guardar el invariante en la propia query**:
   ```sql
   UPDATE InventoryProduct p
      SET p.stock = p.stock + :qty,
          p.reservedStock = p.reservedStock - :qty, ...
    WHERE p.productCode = :code
      AND p.reservedStock >= :qty      -- <-- guard que hoy NO existe
   ```
   Con el guard, `releaseAtomically` retorna `0` filas → el servicio responde un **error de negocio controlado
   (400/409)**, en vez de reventar el CHECK de la BD con un 500.
3. La task G debe **verificar también** que el índice y el CHECK se apliquen a **BDs ya inicializadas**
   (hoy solo se crean al inicializar una BD nueva): sin migración, una BD existente queda sin índice → sin
   protección de idempotencia.
4. El escenario de aceptación de S4 ("50 threads: 25 reservan, 25 liberan") **no es el camino que produce
   `reservedStock < 0`**. El camino real es liberar **más de lo reservado**. La prueba debe reflecting eso:
   `release` de una cantidad mayor que la reservada → error controlado, `reserved_stock` nunca < 0.

---

## E3 — El caso "mismo `orderNumber`, 2 hilos concurrentes" necesita una decisión explícita

B4-08 (S3) propone `SELECT ... FOR UPDATE` **sobre la búsqueda del movimiento previo** o `ON CONFLICT DO
NOTHING` en el INSERT. Con el esquema actual, `FOR UPDATE` sobre `stock_movement_history` **no serializa**
dos transacciones que aún no han insertado la fila (no hay fila que bloquear) → el `FOR UPDATE` solo sirve
como **bloqueo deGap/advisory**, no como lock real sobre la fila faltante.

Evidencia del conflicto (probe): el índice único **sí** salta:

```
P3 A check -> Optional.empty
P3 A insert -> DataIntegrityViolationException (indice unico violado)
```

**Corrección obligatoria:** el fix debe tratar el `DataIntegrityViolationException` como **una respuesta
idempotente válida** (re-leer el movimiento ganador y devolver 200), no como un error 500. Eso vale tanto para
el índice actual como para el redefinido en E1. Sin ese manejo, 20 threads simultáneos **no** dan "20 × 200"
como pide el criterio de aceptación de S3.

---

## Checklist de aplicación (para quien implemente B4-08)

- [ ] La idempotencia se keyea por **`(orderNumber, productCode)`**, no solo por `orderNumber` (E1)
- [ ] El criterio de aceptación incluye el caso **mismo orderNumber + producto DISTINTO** (E1)
- [ ] Se decide y documenta el **nuevo índice único** (incluye `product_code` o no) y se aplica a **BDs
      existentes** (migración, no solo `CREATE INDEX IF NOT EXISTS` en init) (E1 + E2.3)
- [ ] `releaseAtomically` lleva el **guard `reservedStock >= :qty`** en la query (E2.2)
- [ ] El `DataIntegrityViolationException` por carrera se traduce a **200 idempotente**, no a 500 (E3)
- [ ] Se elimina/reformula la premisa de S4 ("no hay constraint CHECK") (E2.1)
- [ ] La prueba de `reservedStock` usa el camino real (liberar más de lo reservado), no 50 threads (E2.4)
