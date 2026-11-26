# Guion de video · Yandris Rivera Torres

Participación en el [guion grupal](GUION_VIDEO.md) de 4:30. Yandris abre el video durante 45 segundos, da paso a Gino y vuelve para cerrar en los últimos 10 segundos.

## Antes de grabar

- Abre el [diagrama de arquitectura](../architecture/architecture.html) en una pestaña.
- En otra pestaña, abre el panel de Eureka en `http://localhost:8761`. Comprueba que aparezcan `API-GATEWAY`, `INVENTORY-SERVICE` y dos instancias de `ORDER-SERVICE` en estado `UP`.
- Deja preparada una terminal con `docker compose ps`. Aumenta el tamaño del texto para que se lean los nombres y los estados.
- Usa el stack local de demostración y evita mostrar credenciales o datos personales.

```bash
docker compose ps
```

## 0:00–0:45 · Arquitectura y descubrimiento

### Narración

> Hola, soy Yandris Rivera. Implementamos un sistema de pedidos e inventario. El cliente entra por Spring Cloud Gateway. Para encontrar los servicios, el Gateway consulta a Eureka, donde se registran sus instancias. Aquí vemos Inventario y las dos instancias de Pedidos en estado UP.
>
> Cuando se crea un pedido, Pedidos llama a Inventario para reservar el stock. En PostgreSQL, las escrituras van al primario y las consultas ordinarias de Pedidos se reparten entre dos réplicas. Ahora Gino va a mostrar cómo comprobamos que el Gateway distribuye las solicitudes entre las dos instancias.

### Qué mostrar

| Tiempo | Pantalla | Qué señalar |
|---|---|---|
| 0:00–0:10 | Diagrama de arquitectura | Cliente → Gateway, puerto 8080. |
| 0:10–0:23 | Panel de Eureka, puerto 8761 | `API-GATEWAY`, `INVENTORY-SERVICE` y los dos identificadores de `ORDER-SERVICE` en `UP`. |
| 0:23–0:39 | Diagrama de arquitectura | Pedidos → Inventario para reservar stock. PostgreSQL primario para escrituras y dos réplicas para las lecturas ordinarias de Pedidos. |
| 0:39–0:45 | Diagrama o panel de Eureka | Deja las dos instancias visibles mientras das paso a Gino. |

Si quieres incluir `docker compose ps`, deja la salida lista y muéstrala brevemente junto al diagrama. Prioriza que se lean las dos instancias en Eureka.

## 4:20–4:30 · Conclusión

### Narración

> Al fallar una instancia o una réplica, las consultas continuaron. Al reiniciar ambas instancias de Pedidos, observamos respuestas 503 antes de que la ruta volviera a estar disponible.

### Qué mostrar

- Deja en pantalla la última toma de Andy con la réplica reincorporada. Si aparece una respuesta real con `X-Database-Node: REPLICA_1`, mantenla visible.
- Si prefieres cerrar sobre los resultados medidos, abre de antemano la sección P02 del [informe experimental](INFORME_P01_P08.md). Señala la fila que registra los `503` iniciales y los `200` en régimen estable.
