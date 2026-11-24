# Apply Progress — B4-12: Tests de integración con PostgreSQL real en CI, sin omitir en silencio

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `098f26f`
ODD: `odd/archive/B4-12-inventory-postgres-integration-tests-ci.md`
Origen: WARN **W3** de la verificación `03-verify-odd` de B4-08 (2026-11-24 11:05)

## Tasks

- [x] A — Workflow de CI: `mvn test` → `mvn verify` para `inventory-service` (failsafe incluido)
- [x] B — Quitar `disabledWithoutDocker = true` + chequeo explícito que **falla** con mensaje accionable
- [x] C — Test de conflicto `ON CONFLICT DO NOTHING` (coordinado con B4-11, task H)
- [x] D — Documentar la ruta local con y sin Docker
- [x] E — Verificación: `verify` con Docker, `test` sin Docker, `spotless:check`

## Definition of Done

- [x] `.github/workflows/ci-inventory-service.yml` corre `mvn verify` para `inventory-service`
- [x] El CI ejecuta y **exige** los ITs de `InventoryReservationIT`
- [x] Sin Postgres disponible, la ejecución **falla** con mensaje explícito (nada de `skipped` silencioso)
- [x] El mensaje dice qué se necesita y cuál es el comando alternativo (`mvn test`) para el ciclo rápido
- [x] El IT de conflicto `ON CONFLICT` (B4-11) está escrito y corre en CI
- [x] La ruta local con/sin Docker está documentada
- [x] `mvn -pl inventory-service verify` pasa en un entorno con Docker
- [x] `mvn -pl inventory-service test` sigue pasando sin Docker (ciclo rápido)
- [x] `mvn -pl inventory-service spotless:check` pasa

## Tests

| Alcance | Antes | Después |
|---|---:|---:|
| `inventory-service` (unit) | 32 | 36 (0 failures, 0 errors, 0 skipped) |
| `inventory-service` IT con Postgres | 4 skipped | **5 ejecutados** (0 failures, 0 errors, 0 skipped) |
| Reactor completo (JDK 21) | 102 | 106 (0 failures, 0 errors, 0 skipped) |

## Desviaciones / TODO / gaps

- Docker/Postgres sí estuvo disponible durante la verificación: `mvn verify -pl inventory-service` ejecutó los 5 ITs
  con 0 fallos, 0 errores y 0 omitidos. El bloqueo originalmente previsto en el apply-progress quedó resuelto.
- Con la política de "fallar si no hay Postgres", `mvn verify -pl inventory-service` **falla** sin Docker con un
  mensaje accionable. El ciclo rápido de desarrollo es `mvn test -pl inventory-service`.
- El IT de conflicto (task C) dependió de la implementación de B4-11 (método `ON CONFLICT`). B4-11 se aplicó
  primero; el test ejecuta con Postgres real y verifica 200 idempotente sin marcar la transacción *rollback-only*.

## Commits

```
1de1baa ci(inventory): run integration tests in workflow
68a3cbd test(inventory): fail integration tests without postgres
245126e docs(inventory): document local Docker test routes
f72f540 test(inventory): cover postgres conflict-safe insert
87470dc test(inventory): pin idempotency guard invariants
```

Verificado con JDK 21: `mvn verify -pl inventory-service` (36 unit + 5 IT, 0 omitidos),
`mvn -q spotless:check -pl inventory-service`, y `mvn test` en el reactor completo (106 tests:
gateway 7, order 63, inventory 36).


## Re-verificación independiente (2026-11-24 12:20)

Con Docker disponible, `mvn -pl inventory-service verify` ejecutó los **5 ITs contra Postgres 15 real**
(0 failures, 0 errors, **0 skipped**) y terminó en `BUILD SUCCESS`. El camino sin Docker ya no omite en
silencio: el IT falla con el mensaje accionable que exige el DoD.

Conteos tras B4-07: 41 unit/slice + 5 ITs en `inventory-service`; **111 tests** en el reactor completo
(gateway 7 · order 63 · inventory 41); `spotless:check` en verde.
