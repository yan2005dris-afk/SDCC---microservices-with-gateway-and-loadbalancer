# Task: Ejecutar y documentar P01–P08

Slug: informe-experimental
Status: completed
Date: 2026-11-24

## Context

Ejecutar y documentar P01–P08

## Specs

S1. Registrar objetivo, procedimiento, expectativa, resultado medido, evidencia y análisis para cada P01–P08 e identificar commit, fecha y entorno.
S2. Medir P02 tras arranque y en régimen estable; capturar HTTP, IDs, correlación, SQL y logs para P01/P04/P05/P07.
S3. Provocar parada y reinicio de una instancia y una réplica en un stack de pruebas autorizado. Registrar solicitudes durante cada fase, tiempos y evidencia WAL y de reincorporación, restaurando ambos componentes.
S4. Si Docker o la inyección de fallos están bloqueados, registrar el bloqueo y los escenarios pendientes. Usar la revisión previa solo como referencia con procedencia explícita, sin inventar datos.

## Tasks

- [x] T1: Capturar línea base P01/P02/P04/P05/P07.
- [x] T2: Provocar y medir interrupciones P03/P06/P08.
- [x] T3: Redactar y verificar informe con anexos.

## Log

- L1: Task scaffolded for slug "informe-experimental": Ejecutar y documentar P01–P08.
- L2: P01–P08 ejecutadas en el stack local; fallos de arranque y resultados válidos se distinguen, las instancias detenidas fueron restauradas y se conservaron volúmenes. Informe y salidas sanitizadas en `docs/plan/INFORME_P01_P08.md` y `docs/evidencias/`.
