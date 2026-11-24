# Task: Corregir el README y el protocolo experimental

Slug: reproducibilidad-entrega
Status: completed
Date: 2026-11-24

## Context

Corregir el README y el protocolo experimental para el cierre de la entrega académica de microservicios.

## Specs

S1. Documentar arranque con espera, .env.example, datos semilla, verificación SQL, apagado sin borrar datos y recuperación de componentes.
S2. Corregir el nombre postgres-replica1, invocar P02 con bash y documentar que la distribución estable se mide después de verificar ambas instancias en tráfico.
S3. Alinear diagrama y texto con los puertos internos y publicados y con el criterio real de aptitud de réplicas. Sustituir garantías absolutas por criterios medibles.

## Tasks

- [x] T1: Documentar arranque con espera, .env.example, datos semilla, verificación SQL, apagado sin borrar datos y recuperación de componentes.
- [x] T2: Corregir el nombre postgres-replica1, invocar P02 con bash y documentar que la distribución estable se mide después de verificar ambas instancias en tráfico.
- [x] T3: Alinear diagrama y texto con los puertos internos y publicados y con el criterio real de aptitud de réplicas. Sustituir garantías absolutas por criterios medibles.

## Log

- L1: Task scaffolded for slug "reproducibilidad-entrega": Corregir el README y el protocolo experimental.
- L2: S1–S3 completadas en README, protocolo y diagramas; `docker compose config -q`, revisión de enlaces y `git diff --check` pasaron. Véase `docs/plan/INFORME_P01_P08.md` y `docs/evidencias/P01-discovery.log`.
