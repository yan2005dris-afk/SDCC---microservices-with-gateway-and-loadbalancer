package com.distribuidos.order.datasource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Pools de tráfico de las réplicas, en el orden de reparto declarado en la configuración.
 *
 * <p>Los pools no son beans individuales (su número depende de la configuración), así que esta
 * clase los cierra al apagar la aplicación.
 */
public final class ReplicaDataSources implements AutoCloseable {

  private final Map<DbNode, DataSource> pools;

  /**
   * Crea el conjunto de pools.
   *
   * @param pools pools por réplica, en orden.
   */
  public ReplicaDataSources(Map<DbNode, DataSource> pools) {
    if (pools.isEmpty()) {
      throw new IllegalArgumentException("Se requiere al menos una réplica de lectura");
    }
    this.pools = Collections.unmodifiableMap(new LinkedHashMap<>(pools));
  }

  /**
   * Réplicas en orden de reparto.
   *
   * @return nodos.
   */
  public List<DbNode> nodes() {
    return List.copyOf(pools.keySet());
  }

  /**
   * Pools por réplica.
   *
   * @return mapa inmutable, en orden.
   */
  public Map<DbNode, DataSource> asMap() {
    return pools;
  }

  @Override
  public void close() throws Exception {
    for (DataSource pool : pools.values()) {
      if (pool instanceof AutoCloseable closeable) {
        closeable.close();
      }
    }
  }
}
