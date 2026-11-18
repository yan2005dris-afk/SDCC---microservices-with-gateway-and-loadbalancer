package com.distribuidos.order.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = RoutingContextHolder.get();
        // Por defecto, si no hay contexto explícito o es nulo, usa PRIMARY
        return (type != null) ? type : DataSourceType.PRIMARY;
    }
}
