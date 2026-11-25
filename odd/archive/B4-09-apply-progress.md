# Apply Progress — B4-09: Cobertura de validación del DTO de reserva (cierre de gaps de B4-01)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `0cfe06b`
ODD: `odd/archive/B4-09-inventory-dto-validation-coverage.md`

## Tasks

- [x] A — Test `quantity` ausente → 400 `"quantity es requerido"` (`a969dbe`)
- [x] B — Test `quantity: null` → 400 `"quantity es requerido"` (`a969dbe`)
- [x] C — Test `productCode` ausente → 400 `"productCode no puede estar vacío"` (`a969dbe`)
- [x] D — Test sin `orderNumber` → 200, `reserveStock("PROD-001", 5, null)` (`a969dbe`)
- [x] E — `verifyNoInteractions(reservationService)` en los 3 casos de validación rechazada (`a969dbe`)
- [x] F — Trazabilidad de B4-01 verificada (ya aplicada en `40dda40`)
- [x] G — `mvn -pl inventory-service verify` + `spotless:check` en verde (`a969dbe`)

## Definition of Done

- [x] Test `quantity` ausente → 400 ProblemDetail `"quantity es requerido"`
- [x] Test `quantity: null` → 400 ProblemDetail `"quantity es requerido"`
- [x] Test `productCode` ausente → 400 ProblemDetail `"productCode no puede estar vacío"`
- [x] Test sin `orderNumber` → 200 y `reserveStock("PROD-001", 5, null)`
- [x] Los 13 tests actuales de `InventoryControllerTest` siguen verdes sin modificarlos
- [x] `verifyNoInteractions(reservationService)` en los 3 casos de validación rechazada
- [x] Trazabilidad de B4-01 correcta (`14 → 21`, DoD marcado)
- [x] `mvn -pl inventory-service verify` pasa
- [x] `mvn -pl inventory-service spotless:check` pasa

## Tests

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 22 | 26 (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 92 | 96 (0 failures, 0 errors) |

## Desviaciones / TODO / gaps

- Las tareas A–E se entregan en un solo commit (`a969dbe`) porque las cinco afectan al mismo archivo de test y forman una sola unidad de cobertura.
- No se tocó `ApiExceptionHandler.java`: pertenece a B4-07. Tampoco se modificaron los 13 tests existentes de `InventoryControllerTest`.
- La tarea F ya estaba aplicada en el repositorio (`40dda40`): `B4-01-apply-progress.md` registra `14 → 21` y el DoD de la spec archivada está marcado. Solo se verificó.
- JDK 25 sigue roto para Spotless 2.44.3 y mocks de Mockito; toda la verificación se ejecutó con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.
- Sin gaps nuevos. Quedan fuera de alcance los huecos propios de B4-06 y B4-08.

## Commits

```
a969dbe test(inventory): cover DTO validation gaps
```

## Verificación independiente (`03-verify-odd`, 2026-11-24 09:12)

Ejecutada sobre `a969dbe` / `4976ec8`:

- `mvn -pl inventory-service test` → **26 tests verdes** (17 controller = 13 previos + 4 nuevos, 9 service).
- **Mutación:** quitando `@NotNull` del DTO en una copia temporal, los 2 tests nuevos fallan con
  `Response status expected:<400> but was:<500>` y los otros 15 siguen verdes.
  → confirma que los tests fijan de verdad el comportamiento y que `@NotNull` es lo que evita el
  `NullPointerException` → 500 del catch-all.
- Los 13 tests preexistentes de `InventoryControllerTest` no fueron modificados.

## Verificación `03-verify-odd` (2026-11-24 09:35) — PASS

**Task completion:** A–G marcadas `[x]` en este apply-progress y en el DoD de la ODD. Archivos tocados por B4-09:
`InventoryControllerTest.java` (+41 líneas, **0 deletions** → los 13 tests preexistentes intactos) y este
apply-progress. Sin trabajo extra.

**Cobertura de mutación (3 mutaciones ejecutadas, 4/4 tests nuevos muerden):**

| Mutación | Resultado |
|---|---|
| Quitar `@NotNull` de `quantity` | `rejectsMissingQuantity` y `rejectsExplicitNullQuantity` → **400 esperado, 500 obtenido**; los otros 15 verdes |
| Quitar `@NotBlank` de `productCode` | `rejectsMissingProductCode` → **400 esperado, 200 obtenido** (+ el preexistente `rejectsBlankProductCode`) |
| `@Nullable` → `@NotBlank` en `orderNumber` | `requestWithoutOrderNumberDelegatesWithNullOrderNumber` → **200 esperado, 400 obtenido** |

**Regresiones:** `inventory-service` 22 → **26** (17 controller + 9 service), 0 failures. Reactor **92 → 96**
(gateway 7 · order 63 · inventory 26), BUILD SUCCESS.

**Calidad:** `mvn -pl inventory-service verify` ✅ · `spotless:check` exit 0 ✅ · JUnit 5 + MockMvc + Mockito
(convención del módulo) ✅ · cada test nuevo falla **un solo campo** (S4 determinista) ✅.

**Commits:** `a969dbe test(inventory): cover DTO validation gaps` (formato convencional ✅, sin tags de IA ni
co-author ✅) y `4976ec8 docs(odd): record B4-09 validation coverage` ✅.

**Nota (no bloqueante):** las tareas A–E van en **un solo commit** porque las 5 tocan el mismo archivo y forman una
única unidad de cobertura. Desviación de "un commit por tarea", aceptada y documentada.
