# Plan: Corregir el README y el protocolo experimental

## 1. Descripción y alcance
Corregir el README y el protocolo experimental para el cierre de la entrega académica de microservicios.

Tamaño estimado: S (~10-50 líneas).
Estrategia de entrega: Delegar a Junie con revisión de los resultados y el alcance de archivos.

## 2. Dependencias previas
- compose.yaml
- README.md

## 3. Specs numeradas
- **S1**: Documentar arranque con espera, .env.example, datos semilla, verificación SQL, apagado sin borrar datos y recuperación de componentes.
- **S2**: Corregir el nombre postgres-replica1, invocar P02 con bash y documentar que la distribución estable se mide después de verificar ambas instancias en tráfico.
- **S3**: Alinear diagrama y texto con los puertos internos y publicados y con el criterio real de aptitud de réplicas. Sustituir garantías absolutas por criterios medibles.

## 4. Contrato de entrada
Repositorio actual en develop, consigna académica, configuración Compose y resultados de la revisión local de P01–P08 como referencia.

## 5. Contrato de salida
Guía que se pueda seguir desde un clon limpio con Docker Compose. No sustituir el enlace pendiente del video.

## 6. Seguridad y resiliencia
Usar datos de demo y omitir credenciales y datos personales de las evidencias.
- Destructive Testing Plan:
  Las verificaciones se limitan a archivos y al stack de prueba autorizado.

## 7. Archivos afectados
- `README.md`
- `docs/plan/TEST_PLAN_P01_P08.md`
