# Plan: Ejecutar y documentar P01–P08

## 1. Descripción y alcance
Ejecutar y documentar P01–P08

Tamaño estimado: L (~500-800 líneas).
Estrategia de entrega: Work Unit decomposition: T1 línea base P01/P02/P04/P05/P07, T2 interrupciones P03/P06/P08, T3 informe y anexos.

## 2. Dependencias previas
- colecciones-medicion
- reproducibilidad-entrega
- compose.yaml

## 3. Specs numeradas
- **S1**: Registrar objetivo, procedimiento, expectativa, resultado medido, evidencia y análisis para cada P01–P08 e identificar commit, fecha y entorno.
- **S2**: Medir P02 tras arranque y en régimen estable; capturar HTTP, IDs, correlación, SQL y logs para P01/P04/P05/P07.
- **S3**: Provocar parada y reinicio de una instancia y una réplica en un stack de pruebas autorizado. Registrar solicitudes durante cada fase, tiempos y evidencia WAL y de reincorporación, restaurando ambos componentes.
- **S4**: Si Docker o la inyección de fallos están bloqueados, registrar el bloqueo y los escenarios pendientes. Usar la revisión previa solo como referencia con procedencia explícita, sin inventar datos.

## 4. Contrato de entrada
Repositorio, configuración Compose y auditoría local como referencia histórica.

## 5. Contrato de salida
Informe enlazado a evidencias sanitizadas y medianamente reproducibles por escenario, con límites y tiempos observados.

## 6. Seguridad y resiliencia
Sanitizar evidencias y usar datos de demo.
- Destructive Testing Plan:
  Solo stack de pruebas autorizado, restaurar la instancia y la réplica tras las paradas y conservar volúmenes.

## 7. Archivos afectados
- `docs/plan/INFORME_P01_P08.md`
- `docs/evidencias/`
