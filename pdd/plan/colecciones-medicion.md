# Plan: Corregir colecciones y medición de solicitudes

## 1. Descripción y alcance
Corregir colecciones y medición de solicitudes para el cierre de la entrega académica de microservicios.

Tamaño estimado: M (~100-400 líneas).
Estrategia de entrega: Delegar a Junie con revisión de los resultados y el alcance de archivos.

## 2. Dependencias previas
- compose.yaml
- README.md

## 3. Specs numeradas
- **S1**: Encadenar el ID real devuelto por el POST en HTTP y Bruno, con ejemplo válido y caso de stock insuficiente.
- **S2**: Medir 100 o más peticiones por tanda, conservando número de solicitud, código HTTP, instancia, nodo y errores de transporte o cabeceras. Reconciliar los totales incluso ante fallos.
- **S3**: Verificar sintaxis y ejemplos, incluido un ciclo exitoso y el manejo de errores con el stack si el entorno lo permite.

## 4. Contrato de entrada
Repositorio actual en develop, consigna académica, configuración Compose y resultados de la revisión local de P01–P08 como referencia.

## 5. Contrato de salida
Colecciones reproducibles y salida P02 apta para anexar al informe.

## 6. Seguridad y resiliencia
Usar datos de demo y omitir credenciales y datos personales de las evidencias.
- Destructive Testing Plan:
  Las verificaciones se limitan a archivos y al stack de prueba autorizado.

## 7. Archivos afectados
- `tests/http/api-tests.http`
- `tests/bruno/collection`
- `scripts/experiments/test-p02-loadbalance.sh`
