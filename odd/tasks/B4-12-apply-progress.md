# Apply Progress — B4-12: Tests de integración con PostgreSQL real en CI, sin omitir en silencio

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `098f26f`
ODD: `odd/tasks/B4-12-inventory-postgres-integration-tests-ci.md`
Origen: WARN **W3** de la verificación `03-verify-odd` de B4-08 (2026-11-24 11:05)

## Tasks

- [x] A — Workflow de CI: `mvn test` → `mvn verify` para `inventory-service` (failsafe incluido)
- [ ] B — Quitar `disabledWithoutDocker = true` + chequeo explícito que **falla** con mensaje accionable
- [ ] C — Test de conflicto `ON CONFLICT DO NOTHING` (coordinado con B4-11, task H)
- [ ] D — Documentar la ruta local con y sin Docker
- [ ] E — Verificación: `verify` con Docker, `test` sin Docker, `spotless:check`

## Definition of Done

- [x] `.github/workflows/ci-inventory-service.yml` corre `mvn verify` para `inventory-service`
- [ ] El CI ejecuta y **exige** los ITs de `InventoryReservationIT`
- [ ] Sin Postgres disponible, la ejecución **falla** con mensaje explícito (nada de `skipped` silencioso)
- [ ] El mensaje dice qué se necesita y cuál es el comando alternativo (`mvn test`) para el ciclo rápido
- [ ] El IT de conflicto `ON CONFLICT` (B4-11) está escrito y corre en CI
- [ ] La ruta local con/sin Docker está documentada
- [ ] `mvn -pl inventory-service verify` pasa en un entorno con Docker
- [ ] `mvn -pl inventory-service test` sigue pasando sin Docker (ciclo rápido)
- [ ] `mvn -pl inventory-service spotless:check` pasa

## Tests

| Alcance | Antes | Después |
|---|---:|---:|
| `inventory-service` (unit) | 32 | 32+ (0 failures, 0 errors, 0 skipped) |
| `inventory-service` IT con Postgres | 4 skipped | **4 ejecutados** (0 failures) |
| Reactor completo (JDK 21) | 102 | 102+ (0 failures, 0 errors, **0 skipped**) |

## Desviaciones / TODO / gaps

- **Esta ODD no se puede verificar en esta máquina**: el daemon de Docker está caído y no hay Postgres local. La
  evidencia de "los ITs pasan" se debe obtener en un entorno con Docker (o en CI). Hasta entonces, el DoD de esta
  ODD **no puede marcarse entero** — se puede marcar A, B y D (código + documentación) y dejar C y E pendientes.
- Con la política de "fallar si no hay Postgres", `mvn verify -pl inventory-service` **falla localmente** sin
  Docker. Es lo decidido; el ciclo rápido de desarrollo es `mvn test -pl inventory-service`.
- El IT de conflicto (task C) depende de la implementación de B4-11 (método `ON CONFLICT`). Mientras B4-11 no
  aplique, el test se puede escribir pero fallaría por comportamiento → **aplicar B4-11 antes de la task C**.

## Commits

```
(ninguno todavía)
```
