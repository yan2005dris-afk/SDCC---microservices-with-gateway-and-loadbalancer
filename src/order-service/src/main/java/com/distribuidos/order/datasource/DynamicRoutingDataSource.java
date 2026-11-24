package com.distribuidos.order.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/** Elige el pool según el nodo fijado en {@link RoutingContextHolder}; sin nodo, el primario. */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

  @Override
  protected Object determineCurrentLookupKey() {
    DbNode node = RoutingContextHolder.get();
    // Por defecto, si no hay contexto explícito o es nulo, usa PRIMARY
    return (node != null) ? node : DbNode.PRIMARY;
  }
}
