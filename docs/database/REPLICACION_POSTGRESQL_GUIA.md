# Guía técnica: Replicación y sincronización en PostgreSQL

**Asignatura:** Aplicaciones Distribuidas  
**Tecnología:** PostgreSQL 17 + Docker Compose  
**Nivel:** Intermedio

## 1. Introducción

PostgreSQL permite replicar información entre servidores para distribuir cargas de lectura, mejorar la disponibilidad de datos y facilitar estrategias de recuperación.

Sin embargo, existen diferencias entre:

- Replicación física y lógica.
- Replicación síncrona y asíncrona.
- Replicación y alta disponibilidad.
- Durabilidad de una transacción y visibilidad de sus datos.

Comprender estas diferencias es fundamental para diseñar sistemas distribuidos confiables.

## 2. Replicación síncrona y asíncrona

### 2.1. Replicación asíncrona

El primario confirma una transacción sin esperar la confirmación de una réplica.

**Secuencia:**

1. El cliente ejecuta una transacción.
2. El primario registra los cambios en WAL.
3. El primario confirma la transacción.
4. La réplica recibe y reproduce los registros WAL.

Los pasos 3 y 4 pueden solaparse; no existe una espera obligatoria de confirmación remota.

**Ventajas:** menor latencia de escritura y mayor independencia respecto a la disponibilidad de las réplicas.

**Desventaja:** si el primario falla antes de que los cambios lleguen a una réplica, pueden perderse transacciones recientes al promoverla.

### 2.2. Replicación síncrona

El primario espera una confirmación de una o varias réplicas elegibles antes de responder al cliente.

El tipo de confirmación depende de `synchronous_commit`.

**Ventaja:** permite aumentar las garantías de durabilidad de transacciones confirmadas.

**Desventaja:** introduce latencia y puede bloquear confirmaciones cuando no hay suficientes réplicas síncronas disponibles.

## 3. Niveles de synchronous_commit

| Valor | Garantía esperada antes de confirmar |
|---|---|
| `off` | No espera el volcado local del WAL a almacenamiento durable |
| `local` | WAL confirmado localmente |
| `remote_write` | WAL escrito en el sistema operativo de la réplica síncrona |
| `on` | WAL persistido de forma durable en la réplica síncrona |
| `remote_apply` | WAL reproducido y cambios visibles para nuevas consultas en la réplica |

Los niveles remotos necesitan una configuración de réplicas síncronas elegibles.

`remote_apply` puede ser útil cuando una operación requiere que sus cambios estén disponibles para nuevas consultas en la réplica antes de confirmar al cliente.

### Ejemplo: operación crítica

```sql
BEGIN;

SET LOCAL synchronous_commit = 'remote_apply';

INSERT INTO pagos (pedido_id, monto, estado)
VALUES (1001, 150.00, 'CONFIRMADO');

COMMIT;
```

La transacción esperará la confirmación de aplicación remota si el primario tiene réplicas síncronas configuradas.

### Ejemplo: operación menos crítica

```sql
BEGIN;

SET LOCAL synchronous_commit = 'local';

INSERT INTO registros_actividad (evento)
VALUES ('Usuario consultó catálogo');

COMMIT;
```

En este caso no se espera confirmación remota.

**Importante:** `synchronous_commit` se configura a nivel de sesión o transacción; no es una propiedad intrínseca de una tabla.

## 4. Configuración síncrona con dos réplicas

Partimos de una arquitectura:

- `primary`: PostgreSQL primario.
- `replica1`: primera réplica.
- `replica2`: segunda réplica.

En el primario:

```yaml
command:
  - postgres
  - -c
  - wal_level=replica
  - -c
  - max_wal_senders=10
  - -c
  - "synchronous_standby_names=ANY 1 (replica1, replica2)"
  - -c
  - synchronous_commit=on
```

`ANY 1` significa que una confirmación de cualquiera de las dos réplicas elegibles satisface el requisito síncrono.

**Requisito indispensable:** cada réplica debe identificarse con el `application_name` correspondiente en su conexión de replicación.

Por ejemplo, en la configuración de conexión de `replica1`:

```text
primary_conninfo = 'host=primary port=5432 user=replicator password=CLAVE application_name=replica1'
```

Y en `replica2`:

```text
primary_conninfo = 'host=primary port=5432 user=replicator password=CLAVE application_name=replica2'
```

Si se utiliza `pg_basebackup -R`, se puede incorporar `application_name` mediante los parámetros de conexión empleados para generar la configuración inicial.

En un entorno real deben utilizarse secretos y mecanismos seguros de gestión de credenciales.

### Verificación

Ejecutar en el primario:

```sql
SELECT
    application_name,
    state,
    sync_state,
    write_lag,
    flush_lag,
    replay_lag
FROM pg_stat_replication;
```

Con ambas réplicas elegibles y conectadas, `sync_state` puede indicar `quorum`.

Los indicadores de latencia pueden ser `NULL` cuando no existe actividad reciente.

## 5. Experimento: disponibilidad frente a durabilidad

### Escenario A: detener una réplica

Con `ANY 1`, detener `replica1`:

```bash
docker compose stop replica1
```

Mientras `replica2` permanezca conectada y elegible, el primario puede seguir confirmando transacciones síncronas.

### Escenario B: detener ambas réplicas

```bash
docker compose stop replica1 replica2
```

Intentar una transacción desde una sesión independiente:

```sql
BEGIN;
SET LOCAL synchronous_commit = 'on';

INSERT INTO prueba_replica (mensaje)
VALUES ('Prueba sin réplicas');

COMMIT;
```

La confirmación puede quedar esperando indefinidamente hasta que vuelva a existir una réplica síncrona elegible o intervenga un mecanismo de cancelación o administración.

En otra terminal:

```bash
docker compose start replica1
```

Observar si el `COMMIT` pendiente puede finalizar.

**Precaución:** cancelar una espera de confirmación no significa necesariamente que la transacción se haya revertido. Antes de reintentar una operación de negocio, hay que comprobar su resultado.

## 6. Replicación física y lógica

### 6.1. Replicación física

Reproduce cambios a nivel del clúster PostgreSQL completo.

Incluye los datos de las bases que pertenecen a esa instancia.

No permite seleccionar individualmente tablas o esquemas para excluirlos de la replicación.

Es apropiada para:

- Réplicas de lectura.
- Recuperación de servidores.
- Estrategias de alta disponibilidad, combinadas con mecanismos de failover.

### 6.2. Replicación lógica

Permite seleccionar conjuntos de tablas mediante publicaciones y suscripciones.

A diferencia de una réplica física, el suscriptor es una instancia PostgreSQL independiente que puede aceptar escrituras, aunque estas deben gestionarse cuidadosamente para evitar conflictos.

La replicación lógica incorporada no reproduce automáticamente todas las operaciones DDL ni el estado de las secuencias.

## 7. Ejemplo de replicación lógica por tablas

Supongamos una base `universidad` con:

```text
universidad
├── academico.estudiantes
├── academico.matriculas
├── academico.asignaturas
├── rrhh.docentes
└── auditoria.eventos
```

Queremos publicar solamente los cambios de estudiantes y matrículas.

En el publicador:

```sql
CREATE PUBLICATION pub_matriculas
FOR TABLE
    academico.estudiantes,
    academico.matriculas;
```

En el suscriptor, primero deben existir tablas compatibles.

Después:

```sql
CREATE SUBSCRIPTION sub_matriculas
CONNECTION
  'host=primary port=5432 dbname=universidad user=replicator password=CLAVE'
PUBLICATION pub_matriculas;
```

Se requiere configurar `wal_level=logical`, permisos de replicación, acceso de red y los demás parámetros de capacidad correspondientes.

Para operaciones `UPDATE` y `DELETE`, las tablas publicadas deben disponer de una identidad de réplica adecuada, normalmente una clave primaria.

## 8. Ejemplo de replicación lógica por esquema

En PostgreSQL 17 podemos publicar todas las tablas admitidas de un esquema:

```sql
CREATE PUBLICATION pub_academico
FOR TABLES IN SCHEMA academico;
```

Esto permite incluir automáticamente las tablas existentes y futuras del esquema que sean elegibles para la publicación.

El suscriptor debe mantener estructuras compatibles. Las modificaciones DDL requieren coordinación independiente.

## 9. Comparación de alcance

| Alcance | Replicación física | Replicación lógica |
|---|---|---|
| Clúster completo | Sí | No necesariamente |
| Base de datos específica | No | Sí |
| Esquema específico | No | Sí |
| Tabla específica | No | Sí |
| Filtrado de filas | No | Sí, con publicaciones compatibles |
| Selección de columnas | No | Sí, con listas de columnas compatibles |
| DDL replicado | Sí | No automáticamente |
| Escritura en suscriptor | No, durante standby | Posible, con precauciones |

## 10. Aplicación a microservicios

Imaginemos un sistema de comercio electrónico:

- Servicio de clientes.
- Servicio de inventario.
- Servicio de pedidos.
- Servicio de facturación.

Cada microservicio debería ser propietario de sus datos y evitar modificaciones directas sobre tablas ajenas.

La replicación física puede utilizarse para proporcionar réplicas de lectura de una infraestructura PostgreSQL.

La replicación lógica puede servir para distribuir proyecciones de datos entre bases independientes, cuando exista una necesidad justificada.

RabbitMQ, en cambio, permite comunicar eventos de negocio, por ejemplo `PedidoCreado` o `FacturaEmitida`.

**Replicación de datos y mensajería no son equivalentes.**

## 11. Actividades experimentales propuestas

### Experimento 1: replicación asíncrona

Crear una tabla en el primario, insertar registros y comprobar su aparición en las réplicas.

Registrar tiempos de visibilidad mediante un cliente de pruebas.

### Experimento 2: replicación síncrona

Configurar `ANY 1` y verificar el estado de las réplicas.

Detener una réplica y comprobar si continúan las confirmaciones.

### Experimento 3: pérdida de réplicas síncronas

Detener ambas réplicas e intentar confirmar una transacción.

Analizar por qué la operación queda esperando.

### Experimento 4: remote_apply

Configurar una transacción con `remote_apply`.

Medir el tiempo de confirmación y comprobar la visibilidad de los datos en una réplica elegible.

### Experimento 5: replicación lógica selectiva

Crear una publicación que incluya únicamente dos tablas.

Insertar registros en tablas publicadas y no publicadas.

Comprobar qué cambios llegan al suscriptor.

## 12. Preguntas de reflexión

1. ¿Por qué la replicación síncrona puede aumentar la latencia de escritura?
2. ¿Qué sucede si todas las réplicas síncronas dejan de estar disponibles?
3. ¿Qué diferencia existe entre `remote_write`, `on` y `remote_apply`?
4. ¿Por qué la replicación física no permite seleccionar tablas?
5. ¿Qué ventajas ofrece la replicación lógica para integrar sistemas?
6. ¿Qué problemas pueden surgir si se modifica directamente una tabla suscrita?
7. ¿La replicación síncrona garantiza alta disponibilidad automática?
8. ¿Qué diferencia existe entre un evento RabbitMQ y un cambio replicado mediante WAL?
9. ¿Cuándo conviene utilizar una réplica de lectura?
10. ¿Qué riesgos existen al consultar datos inmediatamente después de escribir en un primario asíncrono?

## 13. Conclusiones

La replicación PostgreSQL permite diseñar infraestructuras con diferentes compromisos entre rendimiento, durabilidad y disponibilidad.

La replicación física es apropiada para mantener copias completas de una instancia, mientras que la lógica permite distribuir selectivamente cambios de tablas.

La sincronización puede controlarse según las necesidades de las transacciones, siempre que se configuren correctamente las réplicas participantes.

En sistemas distribuidos, elegir una estrategia de replicación requiere analizar tanto los escenarios normales como los fallos y las condiciones de recuperación.

## Referencias técnicas

- [PostgreSQL 17 — Streaming Replication](https://www.postgresql.org/docs/17/warm-standby.html#STREAMING-REPLICATION)
- [PostgreSQL 17 — Synchronous Replication](https://www.postgresql.org/docs/17/warm-standby.html#SYNCHRONOUS-REPLICATION)
- [PostgreSQL 17 — Logical Replication](https://www.postgresql.org/docs/17/logical-replication.html)
- [PostgreSQL 17 — CREATE PUBLICATION](https://www.postgresql.org/docs/17/sql-createpublication.html)
- [PostgreSQL 17 — Runtime Configuration: WAL](https://www.postgresql.org/docs/17/runtime-config-wal.html)