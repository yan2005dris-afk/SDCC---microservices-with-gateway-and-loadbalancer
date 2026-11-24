# Task: Corregir colecciones y medición de solicitudes

Slug: colecciones-medicion
Status: completed
Date: 2026-11-24

## Context

Corregir colecciones y medición de solicitudes para el cierre de la entrega académica de microservicios.

## Specs

S1. Encadenar el ID real devuelto por el POST en HTTP y Bruno, con ejemplo válido y caso de stock insuficiente.
S2. Medir 100 o más peticiones por tanda, conservando número de solicitud, código HTTP, instancia, nodo y errores de transporte o cabeceras. Reconciliar los totales incluso ante fallos.
S3. Verificar sintaxis y ejemplos, incluido un ciclo exitoso y el manejo de errores con el stack si el entorno lo permite.

## Tasks

- [x] T1: Encadenar el ID real devuelto por el POST en HTTP y Bruno, con ejemplo válido y caso de stock insuficiente.
- [x] T2: Medir 100 o más peticiones por tanda, conservando número de solicitud, código HTTP, instancia, nodo y errores de transporte o cabeceras. Reconciliar los totales incluso ante fallos.
- [x] T3: Verificar sintaxis y ejemplos, incluido un ciclo exitoso y el manejo de errores con el stack si el entorno lo permite.

## Log

- L1: Task scaffolded for slug "colecciones-medicion": Corregir colecciones y medición de solicitudes.
- L2: S1–S3 verificadas con Bruno 6/6 y 13/13 aserciones; P02 registra 100 solicitudes y reconcilia el caso de transporte. La ejecución de `tests/http/api-tests.http` con `ijhttp` queda `not_run` porque esa CLI no está instalada; secuencia revisada y ciclo equivalente ejercitado con Bruno/cURL. Evidencia en `docs/plan/INFORME_P01_P08.md`.
