# Apply Progress — B4-13: Observabilidad y alineación del plan en `inventory-service`

Rama: `brydyan/feature/inventory-reserve-hardening`
ODD: `odd/archive/B4-13-inventory-observability-plan-alignment.md`
Estado: **PASS** (verificado el 2026-11-24 13:50 con Postgres 15 real)

> B4-13 no es una mejora estética: cierra los 5 ítems del checklist de B4 en `docs/plan/PLAN_IMPLEMENTACION.md`
> que nunca llegaron a ODD (ni a tarea de ninguna ODD del bloque) y que seguían sin implementarse. Con esta
> ODD, el bloque B4 queda completo: el plan, el código y las pruebas dicen lo mismo.

## Tasks

- [x] A — `MdcKeys` + `InstanceIdentity` + `CorrelationIdFilter` (mismo patrón que `order-service`)
- [x] B — El filtro pone `X-Instance-Id` y `X-Correlation-Id`; el controller deja de poner la cabecera a mano
- [x] C — Log estructurado `stock.reserve` con `result=OK|INSUFFICIENT`
- [x] D — `spring.jpa.open-in-view: false` + `logging.pattern.level` con `cid`/`inst`
- [x] E — `idx_stock_movement_product_code` en DDL de producción, script de migración y esquema de test
- [x] F — Test de contención: stock 5, 20 hilos → 5 éxitos, 15 rechazos, 5 movimientos
- [x] G — Tests de filtro, de log estructurado y de existencia del índice
- [x] H — Trazabilidad: `PLAN_IMPLEMENTACION.md` + `TAREAS_PENDIENTES.md`
- [x] I — Verificación: `mvn verify` (con Docker) + reactor + `spotless:check` + batería de mutaciones

## Definition of Done

- [x] `X-Instance-Id` lo pone un filtro, no cada método del controller
- [x] El filtro devuelve `X-Correlation-Id` y lo deja en el MDC; el MDC se limpia al terminar
- [x] `logging.pattern.level` incluye `cid` e `inst`
- [x] Log `stock.reserve code=… qty=… order=… result=OK|INSUFFICIENT remaining=…`
- [x] `spring.jpa.open-in-view: false` y sin warning de Hibernate en el arranque
- [x] `idx_stock_movement_product_code` en producción, migración y esquema de test
- [x] IT de contención: stock 5 → 5 éxitos, 15 rechazos, stock 0, 5 movimientos
- [x] IT que verifica la existencia del índice
- [x] Tests del filtro (cabeceras + MDC) y del log estructurado
- [x] `PLAN_IMPLEMENTACION.md` y `TAREAS_PENDIENTES.md` con la trazabilidad de INV-01…INV-06 al día
- [x] `mvn -pl inventory-service verify` pasa (52 unit/slice + 14 IT con Postgres real, 0 omitidos)
- [x] `mvn verify` del reactor pasa
- [x] `mvn -pl inventory-service spotless:check` pasa
- [x] Batería de mutaciones: cada comportamiento nuevo está fijado por un test que lo detecta (10/10)

## Escenarios del spec cubiertos

| Escenario del spec | Evidencia | Estado |
|---|---|---|
| S1 — `X-Instance-Id` en todas las respuestas, incluso 404 | `CorrelationIdFilterTest.setsInstanceHeaderOnEveryResponse`, `InventoryControllerTest.everyResponseCarriesInstanceAndCorrelationHeaders`, `…unmappedPathAlsoCarriesInstanceAndCorrelationHeaders` | ✔ nuevo |
| S2 — MDC con `cid`/`inst`, limpiado al terminar | `CorrelationIdFilterTest.putsIncomingCorrelationIdAndInstanceInMdcDuringRequestAndClearsThemAfter`, `…clearsMdcWhenTheChainFails` | ✔ nuevo |
| S2 — el `cid` entrante se propaga | `InventoryControllerTest.incomingCorrelationIdIsPropagatedToTheResponse` | ✔ nuevo |
| S3 — log `result=OK` con el `remaining` | `InventoryReservationLoggingTest.successfulReserveLogsOkWithRemainingStock` | ✔ nuevo |
| S3 — log `result=INSUFFICIENT` sin traza | `InventoryReservationLoggingTest.rejectedReserveLogsInsufficientWithAvailableStock` | ✔ nuevo |
| S4 — `open-in-view: false` y sin warning de arranque | `InventoryReservationIT.jpaOpenInViewIsDisabled` + arranque sin el warning de Hibernate | ✔ nuevo |
| S4 — el GET sigue serializando sin sesión abierta | `InventoryReservationIT.getProductSerializesEntityWithoutOpenSessionInView` | ✔ nuevo |
| S5 — el índice existe en Postgres real | `InventoryReservationIT.schemaHasIndexOnStockMovementProductCode` | ✔ nuevo |
| S6 — contención con stock 5 | `InventoryReservationIT.twentyConcurrentReservesWithScarceStockYieldFiveSuccessesAndFifteenRejections` | ✔ nuevo |
| S7 — trazabilidad INV-01…INV-06 | `PLAN_IMPLEMENTACION.md` (checklist, Tests, tabla de ramas, trazabilidad) y `TAREAS_PENDIENTES.md` | ✔ |
| S8 — no se rompe el contrato ni la suite | 142 tests en verde en el reactor; los 22 tests previos de `InventoryControllerTest` intactos | ✔ |

## Tests

| Alcance | Antes | Después |
|---|---:|---:|
| `InventoryControllerTest` | 22 | 25 (0 fallos) |
| `InventoryReservationServiceTest` | 13 | 13 (0 fallos) |
| `InventoryProductRepositoryJpaTest` (H2) | 6 | 6 (0 fallos) |
| `InventoryReservationLoggingTest` | — | 4 (0 fallos) |
| `CorrelationIdFilterTest` (observability) | — | 4 (0 fallos) |
| `InventoryReservationIT` (Postgres 15 real) | 12 | **14** (0 fallos, 0 omitidos) |
| **Total `inventory-service`** | 53 | **66** (0 fallos, 0 omitidos) |
| **Total reactor** | 129 | **142** (gateway 7, order 63 + 6 IT, inventory 52 + 14 IT) |

## Batería de mutaciones sobre los comportamientos nuevos

Un test que no detecta la mutación no fija nada. 10 mutantes dirigidos, todos **muertos** y cada uno por
aserción (se verificó que ninguno murió por error de compilación):

| Mutación | Test que la detecta | Resultado |
|---|---|---|
| M1 — el filtro no pone `X-Instance-Id` | `CorrelationIdFilterTest` + `InventoryControllerTest` | 💀 murió |
| M2 — el filtro no limpia el MDC | `CorrelationIdFilterTest` | 💀 murió |
| M3 — el filtro ignora el `X-Correlation-Id` entrante | `CorrelationIdFilterTest` + `InventoryControllerTest` | 💀 murió |
| M4 — el filtro no pone `X-Correlation-Id` | `CorrelationIdFilterTest` + `InventoryControllerTest` | 💀 murió |
| M5 — el log dice `result=RESERVED` en vez de `OK` | `InventoryReservationLoggingTest` | 💀 murió |
| M6 — el log omite `remaining=` | `InventoryReservationLoggingTest` | 💀 murió |
| M7 — el log dice `OK` en vez de `INSUFFICIENT` | `InventoryReservationLoggingTest` | 💀 murió |
| M8 — el esquema de test no crea el índice | `InventoryReservationIT.schemaHasIndexOnStockMovementProductCode` | 💀 murió |
| M9 — `open-in-view` vuelve a `true` | `InventoryReservationIT.jpaOpenInViewIsDisabled` | 💀 murió |
| M10 — la reserva permite sobreventa (se quita `stock >= :qty`) | `InventoryReservationIT.twentyConcurrentReservesWithScarceStock…` | 💀 murió |

## Desviaciones / TODO / gaps

- **Un solo filtro en vez de dos.** El plan pedía `InstanceIdHeaderFilter` y `CorrelationIdFilter`. Se
  implementó **un** filtro (`CorrelationIdFilter`) que pone ambas cabeceras: el requisito de fondo —"un
  filtro en todas las respuestas, no en cada método"— se cumple mejor así, y el patrón es el mismo de
  `order-service`. Queda anotado en el checklist del plan.
- **`@Import(InstanceIdentity.class)` en `InventoryControllerTest`.** El `@WebMvcTest` carga el filtro y
  necesita su bean; es el mismo apaño que ya usa `OrderControllerTest`. No se tocó ninguna aserción de los
  22 tests previos: siguen intactos y en verde.
- **Verificación manual de P02** (ejecución del script de pedidos concurrentes) queda fuera de esta ODD: es
  una corrida de P02, del bloque B7/B9. En el plan queda como el único checkbox de B4 sin marcar, y no se
  marcó como hecho.
- **El ítem "pasa en CI"** (como en B4-12) se verificó en su equivalente local: mismo comando, mismo JDK 21,
  Docker disponible. La corrida remota se confirma en el pipeline del próximo push.

## Commits

```
c1938a0 feat(inventory): add observability filters, reserve log and plan alignment
```
