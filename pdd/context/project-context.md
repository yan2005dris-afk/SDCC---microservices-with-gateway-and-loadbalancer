# Contexto del proyecto — pandorx

Este archivo es una **vista materializada compilada** a partir de las
decisiones de `pandorx init`. No se edita a mano: se regenera cada vez
que el init o el flujo de configuración se ejecuta. Cada decisión tiene
su ADR individual en `pdd/context/architecture/decisions/`.

## Decisiones por etapa

### Backend API / Microservice (headless services, platform integration). (system_nature)

- **Procedencia:** user
- **Decisión:** B
- **Justificación:** Arquitectura de microservicios distribuidos con Spring Boot, Gateway y Eureka
- **Contenido:** ## Nature and domain of the system

- Question: What is the domain, business model and type of system you are building?
- Answer: B — Backend API / Microservice (headless services, platform integration).
- Source: user

### Greenfield / new product: initial functional release with defined boundaries. (scope_mvp)

- **Procedencia:** user
- **Decisión:** A
- **Justificación:** Greenfield académico con implementación de 2 microservicios, gateway, eureka y BD replicada
- **Contenido:** ## System maturity and intervention horizon

- Question: What is the lifecycle maturity of this system and the scope of this intervention?
- Answer: A — Greenfield / new product: initial functional release with defined boundaries.
- Source: user

### Moderate concurrency (< 1,000 req/min), standard web traffic. (concurrency_load)

- **Procedencia:** user
- **Decisión:** B
- **Justificación:** Pruebas de carga académica y balanceo HTTP con distribución de 100+ solicitudes
- **Contenido:** ## Concurrency, load and performance

- Question: What is the expected usage volume and performance profile?
- Answer: B — Moderate concurrency (< 1,000 req/min), standard web traffic.
- Source: user

### Relational database (PostgreSQL, MySQL, SQLite). (data_architecture)

- **Procedencia:** user
- **Decisión:** A
- **Justificación:** PostgreSQL con replicación WAL: 1 nodo primario (escritura) y 2 réplicas (lectura) con enrutamiento dinámico
- **Contenido:** ## Data architecture and persistence

- Question: How will the system store and manage data?
- Answer: A — Relational database (PostgreSQL, MySQL, SQLite).
- Source: user

### No authentication needed (public tool or internal script). (auth_security)

- **Procedencia:** user
- **Decisión:** A
- **Justificación:** No se requiere autenticación para el alcance de la prueba de distribución y balanceo
- **Contenido:** ## Authentication and security

- Question: Does the system require user authentication?
- Answer: A — No authentication needed (public tool or internal script).
- Source: user

### Unit + integration tests with CI automation. (testing_strategy)

- **Procedencia:** user
- **Decisión:** B
- **Justificación:** Pruebas experimentales P01 a P08 automatizadas y scripts de validación con colecciones HTTP
- **Contenido:** ## Testing and quality strategy

- Question: What testing approach do you want from the start?
- Answer: B — Unit + integration tests with CI automation.
- Source: user

### Container orchestration (Docker Compose / Podman Quadlet). (infrastructure_deployment)

- **Procedencia:** user
- **Decisión:** C
- **Justificación:** Docker Compose para orquestar Gateway, Eureka, Microservicios y cluster PostgreSQL primario + 2 replicas
- **Contenido:** ## Infrastructure and deployment

- Question: How will this system be deployed and run?
- Answer: C — Container orchestration (Docker Compose / Podman Quadlet).
- Source: user

### Basic structured logging (stdout / file). (observability)

- **Procedencia:** user
- **Decisión:** B
- **Justificación:** Logs estructurados en stdout/Docker logs para verificar enrutamiento, balances y fallos
- **Contenido:** ## Observability and monitoring

- Question: How will you monitor the system in production?
- Answer: B — Basic structured logging (stdout / file).
- Source: user

## Guardrails activos

Ningún guardrail definido aún. Los agentes no tienen restricciones de negocio vinculantes más allá del código.

---
Generado automáticamente por `pandorx init` el 18 de enero de 2026.
