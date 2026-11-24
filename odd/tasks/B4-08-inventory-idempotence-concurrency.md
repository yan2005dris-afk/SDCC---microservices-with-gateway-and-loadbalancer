# ODD — B4-08: Idempotencia + Concurrencia en `/reserve` y `/release` (INV-06 rework)

**Rama:** `brydyan/feature/inventory-reserve-hardening`  
**Depende de:** B4-02 (UPDATE atómico), B4-05 (B4-05-apply-progress FAIL registrado)  
**Bloquea:** B4-06  
**Responsable:** Andy

> Rework de B4-05 (verificación = FAIL). No reemplaza B4-05: B4-05 sigue abierta
> hasta que esta ODD cierre y sus criterios de aceptación pasen.

---

## Spec

### S1 — Idempotencia de `/reserve` per orderNumber

**Problema:**
- Dos peticiones con el mismo `orderNumber` descuentan el stock **dos veces**.
- Índice único `(reference_order_number, movement_type)` existe pero no se aprovecha.
- Resultado esperado per spec de B4-05: "segunda retorna 200, stock solo decrementó una vez".

**Solución:**
1. Buscar `StockMovement(reference_order_number=orderNumber, type=RESERVATION)` **antes** de descontar.
2. Si existe → devolver 200 con el movimiento anterior (cantidad, resultado).
3. Si no existe → descontar + insertar movimiento.
4. TODO en una transacción.

**Hecho cuando:**
```
POST /reserve ORD-001 PROD-001 qty=5 → stock 50→45, reserved 0→5, movimiento RESERVATION
POST /reserve ORD-001 PROD-001 qty=5 → 200 OK, stock 45→45 (sin cambiar), movimiento anterior
```

---

### S2 — Idempotencia de `/release` per orderNumber (ya hecha, confirmar)

**Problema:**
- Especificación de B4-05 dice "idempotente", pero no hay test de segunda llamada.

**Solución (ya en B4-05):**
1. Buscar `StockMovement(reference_order_number=orderNumber, type=RELEASE)`.
2. Si existe → devolver 200 (ya liberada).
3. Si no existe → liberar + insertar RELEASE.

**Hecho cuando:** dos `POST /release ORD-001` → ambas 200, stock/reserved sin cambios en segunda.

---

### S3 — Concurrencia en `/reserve` (2+ threads, mismo orderNumber)

**Problema:**
- Si dos threads llaman `POST /reserve ORD-001` juntas:
  - Thread A busca movimiento → no existe → descuenta + inserta
  - Thread B busca movimiento → no existe (aún, porque A aún no hizo flush) → intenta descontar
  - Resulta: 500 `ConstraintViolationException` (índice único viola)

**Solución:**
- Usar `SELECT ... FOR UPDATE` en la búsqueda de movimiento previo, o
- Usar `ON CONFLICT (...) DO NOTHING` en el INSERT del movimiento.
- En cualquier caso: si falla el INSERT (violación), la lectura anterior devolvió movimiento → es 200 (no error).

**Hecho cuando:**
```
20 threads simultáneos: POST /reserve ORD-001 PROD-001 qty=1
Esperado: 1 éxito (200), 19 idempotentes (200), stock solo decrementó 1 vez
Real antes: N 500 ConstraintViolationException
Real después: 20 × 200
```

---

### S4 — `reservedStock` no puede ser negativo

**Problema:**
- Si `/reserve` descuenta y `/release` luego suma, en concurrencia puede quedar **reservedStock < 0**.
- Causa: no hay constraint `CHECK (reserved_stock >= 0)` en tabla, o no funciona en transacciones simultáneas.

**Solución:**
- Verificar constraint en `02-init-schemas.sql`: `CHECK (reserved_stock >= 0)`.
- Tests de concurrencia verifican que final reservedStock >= 0 (nunca negativo).

**Hecho cuando:**
```
50 threads: 25 reservan, 25 liberan (mismo producto, mismo orderNumber prefijo)
Final: stock >= 0, reserved_stock >= 0 (invariantes mantenidas)
```

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Buscar movimiento anterior en `InventoryReservationService#reserve` antes de descontar | `service/InventoryReservationService.java` |
| B | Si movimiento existe → devolver 200 con la cantidad anterior (idempotencia) | `service/InventoryReservationService.java` |
| C | Si no existe → descontar (B4-02 UPDATE atómico) + insertar movimiento (una tx) | `service/InventoryReservationService.java` |
| D | Manejar `ConstraintViolationException` en INSERT: interpretarlo como idempotencia, buscar movimiento, devolver 200 | `service/InventoryReservationService.java` |
| E | Test concurrencia: 20 threads `/reserve` mismo orderNumber → 1 éxito, 19 idempotentes | `src/test/java/.../InventoryReservationIT.java` |
| F | Test: 50 threads (reserva + release) → invariantes stock/reserved >= 0 | `src/test/java/.../InventoryReservationIT.java` |
| G | Verificar constraint `CHECK (reserved_stock >= 0)` en SQL | `docker/postgres/02-init-schemas.sql` |
| H | Verificación: `mvn verify -pl inventory-service` + 20 iteraciones concurrentes | — |

---

## Definition of Done

- [ ] Búsqueda de movimiento anterior por `reference_order_number` + `movement_type`
- [ ] Segunda `/reserve` del mismo orderNumber → 200 (no descuenta)
- [ ] `ConstraintViolationException` capturado y tratado como idempotencia
- [ ] 20 threads `/reserve` ORD-001 → 20 × 200, stock decrementó 1 vez
- [ ] 50 threads (reserve + release) → stock >= 0 y reserved_stock >= 0
- [ ] Constraint `CHECK (reserved_stock >= 0)` en tabla
- [ ] Tests concurrencia verdes (Testcontainers PostgreSQL real)
- [ ] `mvn verify -pl inventory-service` pasa
- [ ] `mvn spotless:check -pl inventory-service` pasa

---

## Log

> ⚠️ **ERRATA OBLIGATORIA:** esta ODD fue corregida tras la re-verificación con probes contra el esquema real
> de Postgres 15 (2026-11-24 09:10). Ver `odd/tasks/B4-08-verification-errata.md` **antes de implementar**:
> la S1 keyea la idempotencia solo por `orderNumber` (reproduce el defecto cross-producto), la S4 parte de la
> premisa falsa de que el `CHECK (reserved_stock >= 0)` no existe (sí está en `02-init-schemas.sql`), y la S3
> necesita manejar el `DataIntegrityViolationException` como 200 idempotente.

**L1:** 2026-11-24 09:00 — B4-08 creado. Rework de B4-05 tras FAIL: idempotencia fallida, concurrencia → 500 en vez de 200, reservedStock puede negativo.
