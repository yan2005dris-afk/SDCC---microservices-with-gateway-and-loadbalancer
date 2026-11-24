# Evidencias experimentales

Resultados capturados localmente el 24-11-2026 en el stack de pruebas `sdcc-pdd-junie-test`. La ejecución usó `compose.yaml` y [compose-test-overlay.yaml](compose-test-overlay.yaml), que publica `order-service-2` en `18083:8081` porque el puerto host 8083 pertenecía a otro proyecto; `compose.yaml` no se modificó. El Gateway y Eureka conservaron sus puertos 8080 y 8761.

Las solicitudes usan nombres de demostración y correos `example.invalid`. Los anexos omiten credenciales, correos personales y cuerpos de respuesta con datos de cliente. Los comandos, códigos de salida, resultados medidos y referencias históricas están en [INFORME_P01_P08.md](../plan/INFORME_P01_P08.md).