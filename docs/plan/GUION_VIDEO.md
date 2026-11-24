# Guion del video demostrativo (4:30)

**Reparto:** Yandris Rivera Torres, Gino Bermúdez, Allison Reyes y Andy Alejandro. Grabar con voz y capturar respuestas/cabeceras reales; usar solo datos de demostración. Duración total prevista: 4 minutos y 30 segundos.

| Tiempo | Responsable | Toma y narración | Señal que debe quedar visible |
|---|---|---|---|
| 0:00–0:45 | Yandris | Presenta cliente → Gateway → Eureka → Orders/Inventory → PostgreSQL Primary y dos réplicas. Muestra `docker compose ps` y abre Eureka. | `API-GATEWAY`, `INVENTORY-SERVICE` y dos instancias `ORDER-SERVICE` `UP`; puertos host y contenedor diferenciados. |
| 0:45–1:30 | Gino | Explica que Eureka `UP` no basta: hace solicitudes al Gateway hasta ver una respuesta 200 de cada instancia. Ejecuta `bash scripts/experiments/test-p02-loadbalance.sh` y comenta los totales medidos. | Códigos, `X-Instance-Id`, `X-Database-Node` y errores; no prometer reparto exacto 50/50. Si aparece 503 al arrancar, mostrarlo como ventana de propagación y no ocultarlo. |
| 1:30–2:30 | Allison | En `tests/bruno/collection`, ejecuta `bru run -r --env local`. Explica la reserva entre `order-service` e `inventory-service`, enseña el pedido aceptado y el caso de stock insuficiente. Muestra la lectura crítica que usa el ID devuelto, no un ID escrito a mano. | `POST` 201 y `X-Database-Node: PRIMARY`; rechazo de stock 400; `GET /api/orders/{orderId}` devuelve el mismo pedido por `PRIMARY`. |
| 2:30–4:20 | Andy | Detiene `order-service-1`, observa lecturas y lo restaura. Después detiene `postgres-replica1`, espera la detección del probe, muestra lecturas desde la réplica disponible, lo restaura y verifica salud, WAL y reincorporación. | Durante el fallo de Orders, `X-Instance-Id`; durante el fallo de BD, `X-Database-Node`. Tras restaurar, `docker compose ps`, `pg_stat_replication` `streaming` y una lectura que vuelva a mostrar `REPLICA_1`. Salud Compose no equivale por sí sola a estar en rotación. |
| 4:20–4:30 | Yandris | Resume las observaciones y límites; recuerda que el enlace se añadirá al README después de validar el acceso. | Sin afirmar garantías no medidas. |

## Comandos y señales para las tomas de fallo

Ejecutar desde la raíz y con el stack local de pruebas activo:

```bash
docker stop --timeout 40 order-service-1
curl -i http://localhost:8080/api/orders
docker start order-service-1

docker stop --timeout 40 postgres-replica1
sleep 4
curl -i http://localhost:8080/api/orders
docker start postgres-replica1
docker compose ps postgres-replica1
docker compose exec -T postgres-primary sh -lc 'psql -U "$POSTGRES_USER" -d postgres -c "SELECT application_name, state, sync_state FROM pg_stat_replication ORDER BY application_name;"'
```

Tras iniciar la réplica, seguir consultando y mostrar cuándo vuelve `X-Database-Node: REPLICA_1`; registrar el tiempo observado. No usar `docker compose down -v`.

## Grabación y entrega (a cargo del equipo)

1. Grabar las cuatro voces y tomas con datos ficticios; no mostrar `.env`, credenciales, correos personales ni cuerpos con datos ajenos.
2. Revisar continuidad, legibilidad de cabeceras, volumen de voz y duración final entre 3 y 5 minutos.
3. Subir el archivo a la plataforma que el equipo autorice y configurar acceso de visualización/streaming para los destinatarios, sin exigir descarga.
4. Probar el enlace en una ventana privada/sin sesión, confirmar que reproduce y que los permisos no revelan material privado.
5. Solo cuando el equipo confirme acceso y permisos, reemplazar el marcador **“Pendiente de grabación con participación de todos los integrantes”** en `README.md` por el enlace real. La grabación y publicación no se realizan en esta tarea.