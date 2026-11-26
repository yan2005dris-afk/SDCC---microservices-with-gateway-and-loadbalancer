# Evidencias experimentales

Resultados capturados localmente el 24-11-2026 en el stack de pruebas `sdcc-pdd-junie-test`. La ejecución usó `compose.yaml` y [compose-test-overlay.yaml](compose-test-overlay.yaml), que publica `order-service-2` en `18083:8081` porque el puerto host 8083 pertenecía a otro proyecto; `compose.yaml` no se modificó. El Gateway y Eureka conservaron sus puertos 8080 y 8761.

Las solicitudes usan nombres de demostración y correos `example.invalid`. Los anexos omiten credenciales, correos personales y cuerpos de respuesta con datos de cliente. Los comandos, códigos de salida y resultados medidos están en el [informe PDF](../../Entregables/pdf/informe.pdf) (resumen en [INFORME_P01_P08.md](../plan/INFORME_P01_P08.md)).
## Evidencias adicionales

| Archivo | Contenido | Fecha |
|---|---|---|
| [P03-instance-failover-full.log](P03-instance-failover-full.log) | P03 antes / durante / después y tiempo de recuperación (`scripts/experiments/test-p03-instance-failover.sh`). | 2026-11-25 |
| [P06-replica-failover-full.log](P06-replica-failover-full.log) | P06 con lecturas inmediatas y logs del selector (`scripts/experiments/test-p06-replica-failover.sh`). | 2026-11-25 |
| [C09-ijhttp.log](C09-ijhttp.log) | Ejecución de `tests/http/api-tests.http` con la CLI `ijhttp`. | 2026-11-25 |
| [C19-mvn-test.log](C19-mvn-test.log) | `mvn test` de todos los módulos. | 2026-11-25 |
| [smoke-develop-final.log](smoke-develop-final.log) | Versión final (`develop`) desde clon limpio siguiendo el README; P01–P08 repetidos. | 2026-11-26 |

El informe completo está en [Entregables/pdf/informe.pdf](../../Entregables/pdf/informe.pdf).
