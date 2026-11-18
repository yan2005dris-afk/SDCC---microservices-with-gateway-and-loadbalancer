<!-- pandorx:profile:start -->
# Project profile

These are the architectural decisions of this project. Treat them as
invariants: a plan that contradicts one of them is a change to the
project, not an implementation of it, and it needs to be raised.

## Nature and domain of the system
- Answer: Backend API / Microservice (headless services, platform integration).
- Stated by the human. Do not change it without saying so.

## System maturity and intervention horizon
- Answer: Greenfield / new product: initial functional release with defined boundaries.
- Stated by the human. Do not change it without saying so.

## Concurrency, load and performance
- Answer: Moderate concurrency (< 1,000 req/min), standard web traffic.
- Stated by the human. Do not change it without saying so.

## Data architecture and persistence
- Answer: Relational database (PostgreSQL, MySQL, SQLite).
- Stated by the human. Do not change it without saying so.

## Authentication and security
- Answer: No authentication needed (public tool or internal script).
- Stated by the human. Do not change it without saying so.

## Testing and quality strategy
- Answer: Unit + integration tests with CI automation.
- Stated by the human. Do not change it without saying so.

## Infrastructure and deployment
- Answer: Container orchestration (Docker Compose / Podman Quadlet).
- Stated by the human. Do not change it without saying so.

## Observability and monitoring
- Answer: Basic structured logging (stdout / file).
- Stated by the human. Do not change it without saying so.
<!-- pandorx:profile:end -->
