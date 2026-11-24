package com.distribuidos.order.datasource;

/**
 * Resultado de una operación enrutada junto con el nodo que realmente la atendió.
 *
 * @param value valor devuelto por la operación.
 * @param node nodo de base de datos que respondió.
 * @param attempts número de intentos necesarios (1 si no hubo failover).
 * @param <T> tipo del valor.
 */
public record RoutedResult<T>(T value, DataSourceType node, int attempts) {}
