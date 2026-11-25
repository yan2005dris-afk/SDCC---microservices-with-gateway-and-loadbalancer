# Informe experimental P01–P08

## Identificación y alcance

- **Fecha del entorno:** 2026-11-24, hora local UTC−05:00.
- **Commit base del checkout:** `a431612`.
- **Stack:** Docker Engine 29.10.0, Docker Compose 5.8.0, PostgreSQL 17; Bruno CLI 4.3.0.
- **Stack de prueba:** proyecto Compose `sdcc-pdd-junie-test`; `compose.yaml` más [`docs/evidencias/compose-test-overlay.yaml`](../evidencias/compose-test-overlay.yaml). El puerto host 8083 estaba ocupado por otro proyecto, por lo que la segunda instancia se publicó temporalmente en 18083; su puerto interno y el registro Eureka no cambiaron. No se modificó Compose de producción.
- **Datos y seguridad:** pedidos con nombres de demostración y correos `example.invalid`. Los anexos omiten cuerpos con datos de cliente y credenciales. Los pedidos de prueba se conservaron en el volumen; no se borraron volúmenes.
- **Índice de salidas:** [docs/evidencias/README.md](../evidencias/README.md).

## Resultados observados P01–P08

| Prueba | Resultado nuevo medido |
|---|---|
| P01 | Eureka HTTP 200; API-GATEWAY, INVENTORY-SERVICE y las dos instancias ORDER-SERVICE `UP`. Healthchecks sanos, cinco productos de inventario y dos conexiones WAL `streaming/async`. |
| P02 | En la primera ráfaga inmediatamente después de que Eureka mostrara `UP`, 100/100 respuestas fueron 503 sin cabeceras de instancia/nodo. Después de verificar tráfico HTTP 200 desde ambas instancias, una tanda de arranque y otra 15 s después dieron 100/100 HTTP 200, 50/50 por instancia y 50/50 por réplica. El fallo inicial se conserva y no se presenta como una muestra estable. |
| P03 | Con `order-service-1` detenido, las 20 solicitudes inmediatas y otras 20 tras 4 s fueron HTTP 200 desde `order-service-2`; la instancia detenida volvió a `healthy` y Eureka `UP`. |
| P04 | El POST válido devolvió 201 y `PRIMARY`, con ID real `4`; el caso de stock insuficiente devolvió 400. La SQL confirmó ID 4 `CONFIRMED`; `order_audit_logs` tuvo cero filas para ese pedido. |
| P05 | Tres GET ordinarios devolvieron HTTP 200 con nodos `REPLICA_2`, `REPLICA_1`, `REPLICA_1`; los CIDs enlazan Gateway y servicios. No se infiere alternancia determinista. |
| P06 | Tras detener `postgres-replica1` y esperar 4 s a la detección, 20/20 GET fueron HTTP 200 desde `REPLICA_2`; al restaurarla quedó `healthy` y el primario mostró dos réplicas `streaming/async`. |
| P07 | GET crítico inmediato al ID real 4 devolvió HTTP 200, el mismo ID y `X-Database-Node: PRIMARY`. |
| P08 | Tras reactivar `postgres-replica1`, Compose indicó `healthy`, el primario mostró dos WAL `streaming/async` y la primera lectura `REPLICA_1` llegó a los 6.906 ms; fue HTTP 200 desde `order-service-2`. |

### Observaciones históricas (no son evidencia de esta ejecución)

El linaje previo `a431612` anotaba como referencia P02 100 HTTP 200 con reparto 50/50; P03 72 HTTP 200 durante una parada y 12 posteriores; P04 201/400; P05 escrituras PRIMARY y lecturas repartidas; P06 35 HTTP 200 durante parada y 12 posteriores; P07 lectura inmediata 200 en PRIMARY; P08 reingreso 5.4 s. Esas cifras no se mezclan con las mediciones nuevas de este informe.

## Registro de verificaciones

Cada fila es una verificación independiente; `Estado` usa exactamente uno de `passed`, `failed`, `not_run` o `inconclusive`. Los estados describen la observación, no sustituyen el resultado requerido por la especificación.

### C01 — Reproducibilidad, puertos y sintaxis del script

- **Specs:** `reproducibilidad-entrega/S1–S3`, `colecciones-medicion/S2`.
- **Requerido:** sí.
- **Comando exacto:** `docker compose -p sdcc-pdd-junie-test -f compose.yaml -f docs/evidencias/compose-test-overlay.yaml --env-file .env.example config -q`; `bash -n scripts/experiments/test-p02-loadbalance.sh`; `git diff --check`.
- **Código de salida:** 0 en los tres comandos.
- **Salida observada:** Compose aceptó la configuración y el overlay; Bash y el diff no reportaron errores.
- **Salida:** validación local (sin archivo de salida).
- **Estado:** passed.

### C02 — P01, descubrimiento y estado base

- **Specs:** `informe-experimental/S1`.
- **Requerido:** sí.
- **Comando exacto:** `docker compose -p sdcc-pdd-junie-test -f compose.yaml -f docs/evidencias/compose-test-overlay.yaml --env-file .env.example ps`; `curl -sS -H 'Accept: application/json' http://localhost:8761/eureka/apps`; consultas `psql` a `inventory_db` y `pg_stat_replication` como aparecen en [`P01-discovery.log`](../evidencias/P01-discovery.log).
- **Resultado reportado:** 0; Eureka 200, Gateway 200 y componentes saludables.
- **Salida observada:** tres aplicaciones registradas; dos Orders `UP`; cinco productos; dos conexiones WAL `streaming/async`.
- **Salida:** [P01-discovery.log](../evidencias/P01-discovery.log).
- **Estado:** passed.

### C03 — P02, diagnóstico inmediato tras Eureka `UP`

- **Specs:** `informe-experimental/S2`.
- **Requerido:** no (diagnóstico de readiness anterior a la muestra válida).
- **Comando exacto:** `docker compose -p sdcc-pdd-junie-test -f compose.yaml -f docs/evidencias/compose-test-overlay.yaml --env-file .env.example stop --timeout 40 order-service-1 order-service-2`; `docker compose -p sdcc-pdd-junie-test -f compose.yaml -f docs/evidencias/compose-test-overlay.yaml --env-file .env.example up -d --wait --wait-timeout 180 order-service-1 order-service-2`; después `bash scripts/experiments/test-p02-loadbalance.sh` al ver Eureka `UP`, antes de confirmar tráfico HTTP 200 desde ambas instancias.
- **Código de salida del script:** 1.
- **Salida observada:** Eureka ya mostraba ambos `UP`, pero 100/100 HTTP 503; 100 errores HTTP y 100 solicitudes sin las dos cabeceras de ruta; 0 errores de transporte. La evidencia no determina por sí sola la causa interna de esa ventana.
- **Salida:** [P02-loadbalance-startup.log](../evidencias/P02-loadbalance-startup.log) y [P02-gateway-503.log](../evidencias/P02-gateway-503.log) (100 líneas `503 SERVICE_UNAVAILABLE`).
- **Estado:** failed.

### C04 — P02, muestra después de readiness de Gateway

- **Specs:** `colecciones-medicion/S2`, `informe-experimental/S2`.
- **Requerido:** sí.
- **Comando exacto:** `curl --silent --show-error --dump-header - --output /dev/null --connect-timeout 3 --max-time 10 --write-out $'\n%{http_code}' http://localhost:8080/api/orders` repetido hasta observar HTTP 200 desde `order-service-1` y `order-service-2`; después `bash scripts/experiments/test-p02-loadbalance.sh`.
- **Código de salida:** 0.
- **Salida observada:** 100/100 HTTP 200, `order-service-1=50`, `order-service-2=50`, `REPLICA_1=50`, `REPLICA_2=50`, cero errores HTTP, transporte o cabeceras; reconciliación 100/100.
- **Salida:** [P02-loadbalance-startup-ready.log](../evidencias/P02-loadbalance-startup-ready.log).
- **Estado:** passed.

### C05 — P02, régimen estable

- **Specs:** `colecciones-medicion/S2`, `informe-experimental/S2`.
- **Requerido:** sí.
- **Comando exacto:** `sleep 15 && bash scripts/experiments/test-p02-loadbalance.sh`.
- **Código de salida:** 0.
- **Salida observada:** 100/100 HTTP 200, 50/50 por instancia y 50/50 por réplica, cero errores; reconciliación 100/100.
- **Salida:** [P02-loadbalance-stable.log](../evidencias/P02-loadbalance-stable.log).
- **Estado:** passed.

### C06 — P02, contabilización de fallos de transporte

- **Specs:** `colecciones-medicion/S2`.
- **Requerido:** sí.
- **Comando exacto:** `GATEWAY_URL=http://127.0.0.1:1 TOTAL_REQUESTS=100 bash scripts/experiments/test-p02-loadbalance.sh`.
- **Código de salida del script:** 1 (esperado para respuestas fallidas).
- **Salida observada:** 100 intentos, 100 fallos curl 7, estado HTTP 000, cero respuestas HTTP, 100 errores de transporte y reconciliación 100/100; el wrapper de comprobación terminó en 0 al verificar la salida esperada.
- **Salida:** [P02-fault-injection.log](../evidencias/P02-fault-injection.log).
- **Estado:** passed.

### C07 — P02, límite mínimo de solicitudes

- **Specs:** `colecciones-medicion/S2`.
- **Requerido:** no (caso de borde adicional).
- **Comando exacto:** `TOTAL_REQUESTS=99 bash scripts/experiments/test-p02-loadbalance.sh`.
- **Código de salida:** 2 (esperado).
- **Salida observada:** el script rechazó explícitamente un valor menor que 100 antes de enviar solicitudes.
- **Salida:** terminal local (sin archivo).
- **Estado:** passed.

### C08 — Colección Bruno, creación, encadenamiento y rechazo

- **Specs:** `colecciones-medicion/S1`, `colecciones-medicion/S3`.
- **Requerido:** sí.
- **Comando exacto:** `cd tests/bruno/collection && bru run -r --env local --reporter-json ../../../docs/evidencias/Bruno-collection-results.json --reporter-skip-body --reporter-skip-all-headers`.
- **Código de salida:** 0.
- **Salida observada:** 6/6 solicitudes y 13/13 aserciones; POST 201, ID dinámico reutilizado por lectura crítica 200 en PRIMARY, consulta de inventario 200 y rechazo por stock 400. El reporte JSON omite cuerpos y cabeceras.
- **Salida:** [Bruno-collection-results.json](../evidencias/Bruno-collection-results.json).
- **Estado:** passed.

### C09 — Ejecución del archivo HTTP Client de JetBrains

- **Specs:** `colecciones-medicion/S1`, `colecciones-medicion/S3`.
- **Requerido:** sí.
- **Comando previsto:** `ijhttp tests/http/api-tests.http`.
- **Resultado:** no ejecutado; `ijhttp` no está instalado en este entorno. Se revisó el encadenamiento `client.global.set("orderId", response.body.id)` y se verificó el ciclo equivalente con Bruno y cURL, pero no se afirma que se haya ejecutado el parser JetBrains.
- **Salida:** no disponible.
- **Estado:** not_run.

### C10 — P03, caída de instancia

- **Specs:** `informe-experimental/S3`.
- **Requerido:** sí.
- **Comando exacto:** `docker stop --timeout 40 order-service-1`; ejecutar `for phase in immediate after-4s; do if [ "$phase" = after-4s ]; then sleep 4; fi; for request in $(seq 1 20); do curl --silent --show-error --dump-header - --output /dev/null --connect-timeout 3 --max-time 10 --write-out $'\n%{http_code}' http://localhost:8080/api/orders; done; done`; restaurar con `docker start order-service-1`.
- **Código de salida:** 0, incluyendo la comprobación de restauración.
- **Salida observada:** 40/40 HTTP 200 solo desde `order-service-2`; componente restaurado `healthy`, Eureka `UP`.
- **Salida:** [P03-order-failover.log](../evidencias/P03-order-failover.log).
- **Estado:** passed.

### C11 — P04, pedido válido y reserva entre servicios

- **Specs:** `informe-experimental/S1`, `informe-experimental/S2`.
- **Requerido:** sí.
- **Comando exacto:** `curl --silent --show-error --include --request POST --header 'Content-Type: application/json' --header 'X-Correlation-Id: sdcc-p04-evidence-order-01' --data '{"customerName":"Cliente Demo","customerEmail":"demo@example.invalid","productCode":"PROD-001","quantity":2,"unitPrice":1899.99}' http://localhost:8080/api/orders`.
- **Resultado reportado:** 201, ID real 4, `X-Database-Node: PRIMARY`; SQL `orders` muestra estado `CONFIRMED`. Logs enlazan Gateway, Order e Inventory por CID.
- **Salida:** [P04-P05-P07-orders.log](../evidencias/P04-P05-P07-orders.log).
- **Estado:** passed.

### C12 — P04, stock insuficiente

- **Specs:** `informe-experimental/S1`, `informe-experimental/S2`.
- **Requerido:** sí.
- **Comando exacto:** `curl --silent --show-error --include --request POST --header 'Content-Type: application/json' --header 'X-Correlation-Id: sdcc-p04-evidence-reject-01' --data '{"customerName":"Cliente Demo Sin Stock","customerEmail":"sin-stock@example.invalid","productCode":"PROD-001","quantity":9999,"unitPrice":1899.99}' http://localhost:8080/api/orders`.
- **Resultado reportado:** HTTP 400; el Gateway y Order/Inventory registraron el CID. No se interpreta la ausencia de `X-Database-Node` en este rechazo como una escritura.
- **Salida:** [P04-P05-P07-orders.log](../evidencias/P04-P05-P07-orders.log).
- **Estado:** passed.

### C13 — P05, lecturas ordinarias y correlación

- **Specs:** `informe-experimental/S1`, `informe-experimental/S2`.
- **Requerido:** sí.
- **Comando exacto:** `for cid in sdcc-p05-evidence-read-01 sdcc-p05-evidence-read-02 sdcc-p05-evidence-read-03; do curl --silent --show-error --include --request GET --header 'Content-Type: application/json' --header "X-Correlation-Id: $cid" http://localhost:8080/api/orders; done`; consulta SQL del pedido 4 en `orders_db`.
- **Resultado reportado:** tres 200 desde `REPLICA_2`, `REPLICA_1`, `REPLICA_1`; los CIDs corresponden a logs de Gateway y Orders; cuatro pedidos visibles en esa muestra.
- **Salida:** [P04-P05-P07-orders.log](../evidencias/P04-P05-P07-orders.log).
- **Estado:** passed.

### C14 — P06, caída de réplica

- **Specs:** `informe-experimental/S3`.
- **Requerido:** sí.
- **Comando exacto:** `docker stop --timeout 40 postgres-replica1`; `sleep 4`; `for request in $(seq 1 20); do curl --silent --show-error --dump-header - --output /dev/null --connect-timeout 3 --max-time 10 --write-out $'\n%{http_code}' http://localhost:8080/api/orders; done`; restaurar con `docker start postgres-replica1`.
- **Código de salida:** 0, incluida la comprobación de restauración.
- **Salida observada:** 20/20 HTTP 200 desde `REPLICA_2`; Compose `healthy` al restaurar y dos filas `streaming` en el primario.
- **Salida:** [P06-replica-failover.log](../evidencias/P06-replica-failover.log).
- **Estado:** passed.

### C15 — P07, lectura crítica por ID real

- **Specs:** `informe-experimental/S1`, `informe-experimental/S2`.
- **Requerido:** sí.
- **Comando exacto:** `curl --silent --show-error --include --request GET --header 'X-Correlation-Id: sdcc-p07-evidence-critical-01' --header 'X-Consistency: critical' http://localhost:8080/api/orders/4`.
- **Resultado reportado:** HTTP 200, mismo ID 4, `X-Database-Node: PRIMARY`; el log contiene el CID.
- **Salida:** [P04-P05-P07-orders.log](../evidencias/P04-P05-P07-orders.log).
- **Estado:** passed.

### C16 — P08, recuperación y reincorporación

- **Specs:** `informe-experimental/S3`.
- **Requerido:** sí.
- **Comando exacto:** `docker stop --timeout 40 postgres-replica1`; `sleep 4`; `restart_ns=$(date +%s%N)`; `docker start postgres-replica1`; `docker inspect -f '{{.State.Health.Status}}' postgres-replica1`; `docker compose -p sdcc-pdd-junie-test -f compose.yaml -f docs/evidencias/compose-test-overlay.yaml --env-file .env.example exec -T postgres-primary sh -lc 'psql -U "$POSTGRES_USER" -d postgres -c "SELECT application_name, state, sync_state FROM pg_stat_replication ORDER BY application_name;"'`; GET con `curl --silent --show-error --dump-header - --output /dev/null --connect-timeout 3 --max-time 10 --write-out $'\n%{http_code}'` hasta `X-Database-Node: REPLICA_1`.
- **Código de salida:** 0.
- **Salida observada:** health Compose `healthy`, dos streams `streaming/async`, primera lectura en 6.906 ms desde `REPLICA_1` (HTTP 200).
- **Salida:** [P08-replica-rejoin.log](../evidencias/P08-replica-rejoin.log).
- **Estado:** passed.

### C17 — Guion y enlaces de entrega

- **Specs:** `video-demostrativo/S1–S3`.
- **Requerido:** sí.
- **Comando exacto:** `test -s docs/plan/GUION_VIDEO.md && python3 -c 'from pathlib import Path; readme=Path("README.md").read_text(); script=Path("docs/plan/GUION_VIDEO.md").read_text(); names=("Yandris","Gino","Allison","Andy"); assert all(name in script for name in names); assert "Pendiente de grabación con participación de todos los integrantes" in readme; assert "docs/plan/GUION_VIDEO.md" in readme; print("video_script=present; four_members=present; README_marker=preserved; link=present")'`.
- **Código de salida:** 0 para existencia; revisión manual completada.
- **Salida observada:** duración 4:30, cuatro responsables, P01/P02/P04/P05/P07/P03/P06/P08, instrucciones de voz, acceso compartido sin descarga y reemplazo posterior del marcador.
- **Salida:** [GUION_VIDEO.md](GUION_VIDEO.md); [README.md](../../README.md).
- **Estado:** passed.

### C18 — Grabación, publicación y enlace real

- **Specs:** `video-demostrativo/S3`.
- **Requerido:** no en esta intervención; corresponde al equipo.
- **Comando:** no ejecutado; requiere grabación y decisión de publicación del equipo.
- **Resultado:** enlace pendiente; el marcador del README se conservó deliberadamente.
- **Salida:** [README.md](../../README.md), sección “Video Demostrativo”.
- **Estado:** not_run.

### C19 — CI y suite completa de microservicios

- **Specs:** validación general (fuera de los cambios de negocio).
- **Requerido:** no; no se modificó código de negocio ni se inició un job CI.
- **Comando/CI:** ningún job CI fue lanzado; `./gradlew test` no se ejecutó.
- **Resultado:** sin logs de CI disponibles; no se afirma un resultado de CI.
- **Salida:** no disponible.
- **Estado:** not_run.

### C20 — Referencias internas Markdown

- **Specs:** `reproducibilidad-entrega/S1–S3`, `video-demostrativo/S1–S3`, `informe-experimental/S1`.
- **Requerido:** sí.
- **Comando exacto:** `python3 -c 'import re,sys; from pathlib import Path; files=[Path("README.md"),Path("docs/plan/TEST_PLAN_P01_P08.md"),Path("docs/plan/INFORME_P01_P08.md"),Path("docs/plan/GUION_VIDEO.md"),Path("docs/evidencias/README.md"),Path("pdd/tasks/reproducibilidad-entrega.md"),Path("pdd/tasks/colecciones-medicion.md"),Path("pdd/tasks/informe-experimental.md"),Path("pdd/tasks/video-demostrativo.md")]; missing=[(str(f),u) for f in files for u in re.findall(r"\[[^\]]+\]\(([^)]+)\)",f.read_text(encoding="utf-8")) if not u.startswith(("http://","https://","mailto:","#")) and not (f.parent/u.split("#",1)[0].split("?",1)[0]).exists()]; print(f"markdown_files={len(files)} missing_links={len(missing)}"); [print(f"{f}: {u}") for f,u in missing]; sys.exit(bool(missing))'`.
- **Código de salida:** 0.
- **Salida observada:** nueve archivos Markdown comprobados, cero enlaces internos rotos.
- **Salida:** validación local (sin archivo de salida).
- **Estado:** passed.

### C21 — Sintaxis de los diagramas HTML

- **Specs:** `reproducibilidad-entrega/S3`.
- **Requerido:** sí.
- **Comando exacto:** `xmllint --html --noout docs/architecture/architecture.html docs/architecture/stack-diagram-database.html`.
- **Código de salida:** 0.
- **Salida observada:** ambos documentos se analizaron sin errores.
- **Salida:** validación local (sin archivo de salida).
- **Estado:** passed.

## Límites y cierre

- `Eureka=UP` no implicó disponibilidad inmediata en Gateway: se observaron 100 respuestas 503 en la primera muestra sin readiness de ruta. El protocolo ahora exige una respuesta HTTP 200 desde cada instancia antes de la medición P02 y conserva la ventana inicial como observación separada.
- La CLI `ijhttp` no está instalada; la ejecución del archivo `tests/http/api-tests.http` queda como verificación pendiente, aunque su secuencia equivalente se cubrió con Bruno/cURL.
- No se creó ni publicó el video. El equipo debe grabarlo, validar permisos de reproducción y agregar el enlace real.