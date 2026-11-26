# Informe experimental P01–P08

> **Fuente de verdad:** [Entregables/pdf/informe.pdf](../../Entregables/pdf/informe.pdf) (código fuente en [informe.tex](../../Entregables/pdf/informe.tex)).
> Este archivo es un resumen para leer desde GitHub. Si difiere del PDF, vale el PDF.

El PDF contiene introducción, arquitectura y metodología; para cada escenario P01–P08, objetivo, procedimiento, resultado esperado, resultado obtenido, evidencia y análisis (con gráficos de P02, P03 y P06); la verificación sobre la versión final; conclusiones y limitaciones, y en el anexo las verificaciones complementarias C01–C21.

## Entorno

- Mediciones principales: 2026-11-24, stack de prueba `sdcc-pdd-junie-test` (`compose.yaml` + [compose-test-overlay.yaml](../evidencias/compose-test-overlay.yaml)).
- Repeticiones ampliadas de P03 y P06, C09 y C19: 2026-11-25.
- Verificación sobre la versión final (`develop`, clon limpio siguiendo solo el README): 2026-11-26.

## Resultados por escenario

| Escenario | Resultado obtenido | Evidencia |
|---|---|---|
| **P01** Descubrimiento dinámico | Gateway, Inventory y las dos instancias de Orders `UP` en Eureka; 8 contenedores `healthy`; 5 productos semilla; 2 réplicas `streaming/async`. | [P01-discovery.log](../evidencias/P01-discovery.log) |
| **P02** Balanceo HTTP | 100/100 HTTP 200; 50/50 por instancia y 50/50 por réplica; 0 errores. Ventana de arranque: lanzada la ráfaga en cuanto Eureka mostró ambas instancias `UP`, el Gateway respondió 100/100 HTTP 503 (causa probable: health-check y caché de 5 s del LoadBalancer; no medida). | [P02-loadbalance-stable.log](../evidencias/P02-loadbalance-stable.log), [P02-loadbalance-startup.log](../evidencias/P02-loadbalance-startup.log) |
| **P03** Fallo de instancia | Antes 20/20, durante 80/80 y después 20/20 HTTP 200, sin errores. Recuperación: `healthy` en 10,6 s; primera respuesta de la instancia en 14,4 s. | [P03-instance-failover-full.log](../evidencias/P03-instance-failover-full.log) |
| **P04** Comunicación entre servicios | Pedido válido 201 en `PRIMARY` (`CONFIRMED`); sin stock 400. El mismo CID aparece en Gateway, Orders e Inventory. | [P04-P05-P07-orders.log](../evidencias/P04-P05-P07-orders.log) |
| **P05** Enrutamiento de BD | Escrituras en `PRIMARY`; lecturas ordinarias en las réplicas (50/50 en las 200 lecturas de P02). | [P04-P05-P07-orders.log](../evidencias/P04-P05-P07-orders.log) |
| **P06** Fallo de réplica | 40/40 lecturas HTTP 200 con la réplica caída. El selector la marca `DOWN` al primer fallo, la saca de rotación y reintenta en la otra réplica (~1 s extra en una sola solicitud). | [P06-replica-failover-full.log](../evidencias/P06-replica-failover-full.log) |
| **P07** Consistencia de lectura | `GET /api/orders/{id}` con `X-Consistency: critical` devuelve el pedido recién creado desde `PRIMARY`. | [P04-P05-P07-orders.log](../evidencias/P04-P05-P07-orders.log) |
| **P08** Recuperación de réplica | La réplica vuelve a `streaming`; primera lectura servida por `REPLICA_1` 6,9 s después de arrancarla (5,3 s en la versión final). | [P08-replica-rejoin.log](../evidencias/P08-replica-rejoin.log) |

## Otras verificaciones

- Colección Bruno: 6/6 solicitudes, 13/13 aserciones ([Bruno-collection-results.json](../evidencias/Bruno-collection-results.json)).
- Archivo HTTP de JetBrains con `ijhttp`: 12/12 solicitudes, 0 fallos ([C09-ijhttp.log](../evidencias/C09-ijhttp.log)).
- `mvn test`: 124 pruebas, 0 fallos ([C19-mvn-test.log](../evidencias/C19-mvn-test.log)).
- Versión final desde clon limpio: instalación según README y P01–P08 repetidos con los mismos resultados ([smoke-develop-final.log](../evidencias/smoke-develop-final.log)).
- Video demostrativo: [https://youtu.be/Hf0oJhigeFc](https://youtu.be/Hf0oJhigeFc).

## Límites

- `UP` en Eureka no implica disponibilidad inmediata en el Gateway; la medición válida de P02 exige antes un HTTP 200 desde cada instancia.
- P03 se midió con apagado ordenado (`docker stop`); una caída abrupta no se midió.
- La replicación es asíncrona: solo las lecturas `X-Consistency: critical` garantizan ver la última escritura. No hay promoción automática del primario (fuera del alcance de la actividad).
- Las pruebas de integración `*IT` no se ejecutaron en la corrida de C19.
