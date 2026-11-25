# B6-01 — Brief de rework (cierre del WARN de verificación)

**Para:** agente implementador
**Contexto de origen:** `odd/verification/B6-01-verification-2026-11-25.md`
**Spec:** `odd/archive/B6-01-inventory-catalog.md`
**Rama:** `brydyan/feature/inventory-stock-mov-restock`
**Base:** `281621d` · **HEAD:** `d97f717`

---

## 0. Contexto que necesitas

B6-01 implementó el catálogo paginado `GET /api/inventory` con `ProductResponse` y `PagedResponse`.
El código está correcto y la suite unitaria está verde (55 tests). El WARN viene de 3 puntos, no de
defectos funcionales del endpoint:

1. **M8 sin evidencia de mutación**: el mutante "el número de página se ignora (`page` siempre 0)"
   no está cubierto por ningún test, porque todos los tests usan `page=0`.
2. **`mvn verify` nunca se pudo ejecutar**: el DoD lo exige y Docker no estaba disponible.
   `InventoryReservationIT` aborta con un mensaje explícito cuando no hay Docker.
3. **Deuda de robustez (opcional)**: `?sort=<propiedad_inexistente>` revienta en la capa JPA con
   `PropertyReferenceException` y sale como **500** genérico; debería ser **400**.

Estado actual verificado por el verificador: 55 tests / 0 failures / 0 errors, 51 en la base
(+4 nuevos, 0 regresiones), 3 commits conventional sin trailers de IA.

---

## 1. Tarea A — Cerrar la mutación M8 (OBLIGATORIA)

### Archivo a tocar
`src/inventory-service/src/test/java/com/distribuidos/inventory/controller/InventoryCatalogControllerTest.java`

### Qué añadir
Un test nuevo en esa clase. Solo test nuevo: **no modifiques** los 4 tests existentes.

```java
@Test
void listProductsUsesRequestedPageNumber() throws Exception {
  when(repository.findAll(any(Pageable.class)))
      .thenReturn(
          new PageImpl<>(
              List.of(product("PROD-003")),
              org.springframework.data.domain.PageRequest.of(1, 2, Sort.by("productCode")),
              5));

  mockMvc
      .perform(get(CATALOG_URL).param("page", "1").param("size", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].productCode").value("PROD-003"))
      .andExpect(jsonPath("$.totalElements").value(5))
      .andExpect(jsonPath("$.currentPage").value(1))
      .andExpect(jsonPath("$.size").value(2));

  verify(repository).findAll(pageable(1, 2));
}
```

`pageable(int, int)` ya existe como helper privado en la clase y construye
`PageRequest.of(page, size, Sort.by("productCode"))` → úsalo tal cual.

### Por qué esto mata al mutante
El `verify(repository).findAll(pageable(1, 2))` exige que el controller propague
`pageable.getPageNumber()`. Si alguien cambia el código a `PageRequest.of(0, ...)`, el `verify`
falla (argumento distinto) y `currentPage` sale 0 en vez de 1. Ambos fallos cierran el hueco.

### Criterio de aceptación
- Test verde.
- **Test rojo primero (RED)**: aplica el mutante a mano (`pageable.getPageNumber()` → `0`), comprueba
  que el test nuevo falla, y revierte el mutante. Deja ambos resultados anotados en el
  apply-progress (igual que se hizo con RED 1 / RED 2 en B6-01). No dejes el mutante aplicado.
- Suite completa sigue verde: 56 tests.

---

## 2. Tarea B — Cerrar `mvn verify` (OBLIGATORIA)

### Por qué
El DoD de la spec tiene `- [ ] mvn verify -pl inventory-service pasa`. Es el único DoD sin marcar.

### Requisitos
- Docker en ejecución (o un PostgreSQL accesible apuntando por variable de entorno).
- **JDK 21 obligatorio**:
  ```bash
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -v   # debe decir Java version: 21.x
  ```
  Con el JDK 25 por defecto de la máquina, Byte Buddy/Mockito falla con
  "Java 25 (69) is not supported by the current version of Byte Buddy" (preexistente, también
  reproduce en la base). No es culpa de este trabajo, pero rompe el build: usa 21.

### Comando
```bash
cd src/inventory-service
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn clean verify
```

### Criterio de aceptación
- `mvn clean verify` termina en **BUILD SUCCESS**, con `InventoryReservationIT` ejecutado contra
  PostgreSQL real (Testcontainers) y en verde.
- Si el entorno **sigue** sin Docker: **no** marques el DoD como cumplido ni inventes el resultado.
  Deja el ítem `[ ]`, anota en `odd/archive/B6-01-apply-progress.md` que sigue bloqueado por
  entorno, y reporta el bloqueo. Un `[x]` sin evidencia verificada es un FAIL de verificación, no un
  PASS.

---

## 3. Tarea C — Validar `sort` contra allowlist (RECOMENDADA, no bloqueante)

### Problema reproducido empíricamente
```
GET /api/inventory?sort=bogus
```
`InventoryController#listProducts` pasa el `Sort` del cliente directo a la capa JPA.
`repository.findAll(PageRequest.of(0, 20, Sort.by("bogus")))` lanza `PropertyReferenceException`
(confirmado con un probe en la capa JPA). El `@ExceptionHandler(Exception.class)` de
`web/ApiExceptionHandler.java` lo captura y devuelve **500 "Error interno del servidor"**.
Un parámetro de cliente inválido no debe ser un error del servidor.

### Fix propuesto (mínimo, aditivo)
En `src/inventory-service/src/main/java/com/distribuidos/inventory/controller/InventoryController.java`:

1. Allowlist de propiedades ordenables, derivada de `InventoryProduct`:
   `productCode`, `name`, `description`, `stock`, `unitPrice`, `createdAt`, `updatedAt`.
2. Filtrar el `Sort` del cliente: quedarse solo con las propiedades de la allowlist.
   - Si el cliente manda `sort=bogus` y no queda ninguna propiedad válida → usar el sort por defecto
     (`Sort.by("productCode")`) en vez de reventar.
   - Si el cliente manda `sort=name,bogus` → conservar `name` y descartar `bogus`.
3. Mantener el comportamiento actual: sin `sort` del cliente → `productCode ASC`; con `sort` válido →
  se respeta (incluido `desc`).

### Restricciones
- **No cambies** el contrato JSON (`content`, `totalElements`, `totalPages`, `currentPage`, `size`).
- **No cambies** el cap de 100 ni el default de 20.
- **No toques** `GET /{productCode}`, `POST /reserve`, `POST /release`.
- Si filtras props inválidas en vez de rechazar, el endpoint **sigue respondiendo 200**. Si prefieres
  rechazar con 400, añade el handler explícito en `web/ApiExceptionHandler.java` y su test. Cualquiera
  de las dos opciones es válida, siempre que quede test y contrato documentado.

### Test a añadir
Mismo archivo de test de la Tarea A (o un test nuevo si queda más limpio):
```java
@Test
void listProductsIgnoresUnknownSortProperty() throws Exception { ... }
```
Verifica que `?sort=bogus` responde 200 y que el `Sort` que llega al repository es el default
`Sort.by("productCode")` (o el filtrado, según la opción elegida).

---

## 4. Alcance y restricciones

- **Archivos que puedes tocar:**
  - `src/inventory-service/src/main/java/com/distribuidos/inventory/controller/InventoryController.java`
  - `src/inventory-service/src/main/java/com/distribuidos/inventory/web/ApiExceptionHandler.java` (solo si eliges la variante 400)
  - `src/inventory-service/src/test/java/com/distribuidos/inventory/controller/InventoryCatalogControllerTest.java`
  - `odd/archive/B6-01-apply-progress.md` (registro de avance)
- **Archivos que NO debes tocar:** cualquier otro. En particular nada de
  `order-service`, `gateway-service`, `docker/postgres/02-init-schemas.sql`,
  `compose.yaml` ni los DTOs `ProductResponse`/`PagedResponse`.
- No toques la suite de `InventoryControllerTest` (25 tests), `InventoryReservationServiceTest` (13),
  `InventoryProductRepositoryJpaTest` (5), `InventoryReservationLoggingTest` (4),
  `CorrelationIdFilterTest` (4). Deben seguir verdes sin cambios.
- No modifiques el ODD (`odd/archive/B6-01-inventory-catalog.md`) ni el reporte de verificación.
  El header del ODD tiene un nombre de rama distinto al real: es cosmético y está fuera de alcance.
- No añadas trabajo lateral (sin refactors, sin logger nuevo, sin tocar el IT).

## 5. Commits

Formato conventional, un commit por unidad de trabajo, **sin trailers de co-autoría de IA**:

```
test(inventory): cover requested page number in catalog endpoint
fix(inventory): validate catalog sort properties against allowlist   # solo si haces la Tarea C
docs(odd): update B6-01 apply progress with verify results
```

## 6. Qué actualizar al final

En `odd/archive/B6-01-apply-progress.md`:
- Marca el ítem de `mvn verify` como `[x]` **solo** si lo ejecutaste con éxito (criterio §2).
- Actualiza la tabla de tests (Before 51 → After 56 si solo haces la Tarea A; ajusta si añades tests).
- Añade las filas de evidencia RED/GREEN de la Tarea A.
- Añade una nota si implementas la Tarea C, con la decisión tomada (filtrar vs 400) y su test.
- Actualiza la lista de commits.
- Si algo quedó bloqueado, déjalo explícito: `[ ]` + motivo + qué falta.

## 7. Checklist de salida

```bash
cd src/inventory-service
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn test        # debe salir verde, 56+ tests
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn clean verify # BUILD SUCCESS con Docker
```
- [ ] Test de `page > 0` añadido y con RED demostrado (mutante aplicado → falla → revertido).
- [ ] `mvn clean verify` en verde (o bloqueo documentado explícitamente).
- [ ] Sort inválido ya no produce 500 (si hiciste la Tarea C), con test que lo cubra.
- [ ] 0 regresiones: los 55 tests previos siguen verdes.
- [ ] Apply-progress actualizado con evidencia real.
- [ ] Commits conventional, sin trailers de IA.

## 8. Entrega esperada del agente implementador

1. Resultado del checklist, marcar cada línea como hecha o bloqueada.
2. Salida de `mvn test` y de `mvn clean verify` (resumen, no el log completo).
3. Lista de commits con hash y mensaje.
4. Diff de `InventoryController.java` si tocaste la Tarea C.
5. Si algo quedó bloqueado: el motivo exacto, sin marcar como cumplido lo que no pudiste verificar.
