# Microservicios con descubrimiento dinámico, balanceo de carga y persistencia replicada
Diseño, implementación y validación experimental de una arquitectura distribuida basada en Spring Cloud y PostgreSQL.

`Spring Boot` , `Eureka` , `Spring Cloud Gateway` , `PostgreSQL` , `Docker`

> ### El desafío
> Un sistema distribuido no demuestra tolerancia a fallos únicamente porque sus componentes se ejecutan en contenedores diferentes. Debe ser capaz de descubrir servicios, distribuir solicitudes, gestionar la persistencia y continuar operando ante fallos parciales.
>
> Su equipo deberá implementar una solución funcional y demostrar experimentalmente su comportamiento bajo condiciones normales y de fallo.

---

## 01. Objetivo de la actividad
Implementar una arquitectura de microservicios utilizando Spring Boot, Spring Cloud Eureka, Spring Cloud Gateway y PostgreSQL replicado, incorporando descubrimiento dinámico, balanceo de solicitudes, separación de lecturas y escrituras, y mecanismos de continuidad operativa ante fallos parciales.

El sistema de información y su dominio de negocio serán definidos por cada equipo. Se valorará la coherencia de la arquitectura con las necesidades reales del sistema propuesto.

---

## 02. Arquitectura mínima
La solución deberá contener los siguientes componentes:

| Componente | Cantidad mínima | Responsabilidad|
| --- | --- | --- |
| Microservicios Spring Boot | 2 servicios de negocio | Procesos y responsabilidades diferenciadas |
| Instancias de servicio | 2 de un mismo servicio | Balanceo y continuidad ante fallos |
| Eureka Server | 1 | Registro y descubrimiento dinámico |
| Spring Cloud Gateway | 1 | Punto de entrada y enrutamiento HTTP |
| PostgreSQL | 1 primario + 2 réplicas | Persistencia y distribución de lecturas |
| Docker Compose | 1 configuración integrada | Despliegue reproducible |

```markdown
Flujo conceptual:

Cliente HTTP
↓
Spring Cloud Gateway
↓ Descubrimiento mediante Eureka
Microservicio A ⇄ Microservicio B
↓
Enrutamiento de persistencia
↓
PostgreSQL Primary (WRITE)
↘ Replicación WAL ↙
Replica 1 / Replica 2 (READ)
```

Aclaración arquitectónica: Eureka administra el descubrimiento de microservicios. No administra las réplicas PostgreSQL. El balanceo HTTP y la selección de conexiones a la base de datos son responsabilidades diferentes.

---

## 03. Requisitos de implementación
### A. Diseño de microservicios
	- Implementar al menos dos microservicios con responsabilidades de negocio claramente diferenciadas.
	- Definir y documentar las operaciones REST expuestas por cada servicio.
	- Implementar al menos una comunicación real entre microservicios utilizando descubrimiento por nombre lógico.
	- Evitar el acceso directo de un microservicio a las tablas privadas de otro.
	- Implementar dos instancias simultáneas de al menos un microservicio.
### B. Descubrimiento y enrutamiento
	- Registrar los microservicios en Eureka Server.
	- Configurar Spring Cloud Gateway como punto de entrada para los clientes.
	- Utilizar Spring Cloud LoadBalancer para seleccionar instancias de servicio.
	- Emplear rutas basadas en nombres lógicos, por ejemplo lb://servicio-pedidos.
	- Demostrar el comportamiento del sistema cuando una instancia se detiene y posteriormente se recupera.
### C. Persistencia replicada
	- Utilizar PostgreSQL con un nodo primario y dos réplicas de lectura.
	- Dirigir las operaciones de modificación al primario.
	- Distribuir las consultas ordinarias entre las réplicas mediante una política implementada y documentada.
	- Gestionar la indisponibilidad de una réplica sin interrumpir las consultas cuando exista otra réplica saludable.
	- Aplicar una estrategia de consistencia para lecturas críticas inmediatamente posteriores a escrituras.
	- Reizcorporar una réplica recuperada únicamente cuando se encuentre disponible y apta para atender consultas.

La estrategia de enrutamiento puede implementarse mediante múltiples DataSource, AbstractRoutingDataSource u otro mecanismo equivalente debidamente justificado. La anotación @Transactional(readOnly = true) por sí sola no constituye un balanceador de consultas.

### D. Contenedorización
	- Proporcionar una configuración Docker Compose que permita reproducir el entorno.
	- Definir redes, variables de entorno y dependencias necesarias.
	- No incluir contraseñas reales ni secretos en el repositorio.
	- Documentar cómo iniciar, detener, verificar y recuperar los componentes.

---

## 04. Pruebas experimentales obligatorias
Cada escenario deberá ejecutarse y documentarse. No se aceptarán capturas de configuración como sustituto de una prueba funcional.

```
**P01 · Descubrimiento dinámico**
Verificar que las instancias se registran correctamente en Eureka y pueden localizarse por nombre lógico.

**Evidencia:** registro de servicios, identificación de instancias y solicitudes funcionales.
```

```
**P02 · Balanceo HTTP**
Ejecutar al menos 100 solicitudes contra un servicio con dos instancias y registrar qué instancia responde cada solicitud.

**Evidencia:** distribución observada, total de respuestas exitosas y errores.
```

```
**P03 · Fallo de instancia**
Detener una instancia del microservicio y comprobar que el sistema continúa atendiendo solicitudes mediante otra instancia.

**Evidencia:** solicitudes antes, durante y después del fallo; tiempo observado de recuperación.
```

```
**P04 · Comunicación entre servicios**
Ejecutar una operación de negocio que requiera una llamada real de un microservicio a otro mediante descubrimiento dinámico.

**Evidencia:** trazas de solicitud y respuesta, resultado funcional y manejo de errores.
```

```
**P05 · Enrutamiento de base de datos**
Demostrar que las escrituras utilizan el primario y las consultas ordinarias se distribuyen entre las dos réplicas.

Evidencia: identificación del nodo utilizado, consultas SQL y registros de aplicación.
```

```
**P06 · Fallo de réplica PostgreSQL**
Detener una réplica y comprobar que las consultas continúan mediante la réplica restante.

**Evidencia:** estado de contenedores, resultados de consultas y registros del mecanismo de selección.
```

```
**P07 · Consistencia de lectura**
Crear un registro y consultarlo inmediatamente mediante una operación crítica dirigida al primario.

**Evidencia:** secuencia de solicitudes, resultado y justificación de la política aplicada.
```

```
**P08 · Recuperación de réplica**
Reiniciar la réplica detenida, comprobar su recuperación y verificar que vuelve a participar en las consultas.

**Evidencia:** estado de replicación, reincorporación y consultas atendidas.
```

> **Importante:** las fallas deberán provocarse durante las pruebas. El equipo debe explicar el comportamiento observado, incluyendo errores transitorios, tiempos de detección y limitaciones de su solución. No se exige que absolutamente todas las solicitudes sobrevivan sin error durante una interrupción.

---

## 05. Entregables
1. Repositorio GitHub. Código fuente completo, estructura de microservicios, configuración de Eureka, Gateway, persistencia y Docker Compose.
2. README.md técnico. Descripción del sistema, arquitectura, requisitos, dependencias, instalación, configuración de ejemplo, ejecución, datos de prueba y procedimientos de recuperación.
3. Diagrama de arquitectura. Representación de componentes, comunicaciones, puertos, descubrimiento y flujo de persistencia.
4. Informe de pruebas. Para cada escenario P01–P08: objetivo, procedimiento, resultado esperado, resultado obtenido, evidencia y análisis.
5. Colección de solicitudes. Postman, Bruno, archivo HTTP o equivalente, con ejemplos reproducibles de las operaciones implementadas.
6. Video demostrativo (3 a 5 minutos). Enlace público o accesible desde el README.md, con participación de todos los integrantes, explicación de la arquitectura, demostración funcional y simulación de fallos de servicios y réplicas PostgreSQL.

```Markdown
Requisito de reproducibilidad
El proyecto deberá poder ejecutarse siguiendo las instrucciones del README, sin depender de configuraciones privadas, archivos omitidos o intervenciones manuales no documentadas.
```

---

## 06. Restricciones y consideraciones
- No se aceptan servicios vacíos creados únicamente para cumplir una cantidad.
- No se aceptan rutas fijas entre microservicios cuando el requisito es descubrimiento mediante Eureka.
- No se considera balanceo la alternancia manual de URLs desde Postman.
- No se permite simular una réplica únicamente con una tabla duplicada en la misma instancia.
- No se exige promoción automática del primario ni failover completo de PostgreSQL.
- No se exige implementar RabbitMQ, Sagas ni transacciones distribuidas en esta actividad.
- Se pueden utilizar herramientas de IA como apoyo, pero el equipo debe comprender, explicar y defender su implementación.

---

## 07. Rúbrica de evaluación

| Criterio | Puntaje |
| --- | --- |
| Arquitectura y separación de responsabilidades | 15 |
| Eureka y descubrimiento dinámico | 15 |
| Gateway y balanceo HTTP | 15 |
| Enrutamiento de lecturas y escrituras | 20 |
| Manejo de fallos y recuperación | 20 |
| Evidencias, documentación y defensa | 15 |
| TOTAL | 100 |

---

## 08. Video demostrativo y defensa técnica
> ### 🎬 Video obligatorio · Duración: 3 a 5 minutos
> Cada equipo deberá presentar un video en el que demuestre el funcionamiento real de su arquitectura distribuida, explique las decisiones técnicas más importantes y evidencie cómo responde el sistema ante fallos parciales.

### Contenido mínimo del video
1. Presentación de la arquitectura: explicar brevemente los microservicios implementados, sus responsabilidades y la función de Eureka, Gateway y PostgreSQL.
2. Descubrimiento y balanceo: mostrar los servicios registrados en Eureka y demostrar cómo las solicitudes se distribuyen entre distintas instancias de un microservicio.
3. Comunicación entre microservicios: ejecutar una operación de negocio que involucre la interacción entre dos servicios.
4. Persistencia replicada: demostrar que las escrituras se realizan en el primario y que las consultas pueden atenderse desde las réplicas.
5. Simulación de fallos: detener una instancia de microservicio y una réplica PostgreSQL, mostrando cómo responde la aplicación ante cada situación.
6. Conclusiones técnicas: explicar brevemente las limitaciones encontradas, las decisiones implementadas y las mejoras posibles.

```Markdown
Condiciones de presentación
Duración máxima de 5 minutos.
Participación de todos los integrantes del equipo.
Demostraciones realizadas sobre el sistema en ejecución.
Explicaciones mediante narración de voz.
Visualización legible de terminales, registros y resultados.
Publicación mediante enlace accesible, sin necesidad de descargar el video.
Incluir el enlace del video en el README.md del repositorio.
```

> **Importante:** El video no sustituye las evidencias técnicas de los escenarios P01–P08. Estas deberán permanecer documentadas en el repositorio. No se aceptarán videos compuestos únicamente por diapositivas, capturas estáticas o explicaciones teóricas sin demostración funcional.

---

## Resultado esperado
Al finalizar, cada equipo deberá disponer de un sistema distribuido ejecutable, documentado y verificable, con servicios descubiertos dinámicamente, solicitudes balanceadas y persistencia replicada con manejo de fallos parciales.

No basta con que el sistema funcione cuando todo está encendido. Deben demostrar qué ocurre cuando algo falla.
