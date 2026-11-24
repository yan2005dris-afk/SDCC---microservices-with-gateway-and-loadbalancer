package com.distribuidos.order.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Nodos de base de datos de {@code order-service}: un primario y N réplicas de lectura.
 *
 * @param username usuario de base de datos.
 * @param password contraseña de base de datos.
 * @param poolMaxSize conexiones máximas por pool de tráfico (primario y cada réplica).
 * @param primary primario.
 * @param replicas réplicas, en orden de reparto.
 */
@Validated
@ConfigurationProperties("sdcc.datasource")
public record RoutingDataSourceProperties(
    @NotBlank String username,
    @NotNull String password,
    @DefaultValue("10") @Min(1) int poolMaxSize,
    @NotNull @Valid Node primary,
    @NotEmpty List<@Valid Replica> replicas) {

  /**
   * Comprueba que los nombres de las réplicas sean únicos y distintos del primario.
   *
   * @param username usuario.
   * @param password contraseña.
   * @param poolMaxSize tamaño máximo del pool.
   * @param primary primario.
   * @param replicas réplicas.
   */
  public RoutingDataSourceProperties {
    replicas = replicas == null ? List.of() : List.copyOf(replicas);
    Set<String> names = new HashSet<>();
    for (Replica replica : replicas) {
      if (replica.name() == null) {
        continue;
      }
      if (DbNode.PRIMARY.name().equals(replica.name()) || !names.add(replica.name())) {
        throw new IllegalArgumentException(
            "Nombre de réplica repetido o reservado: " + replica.name());
      }
    }
  }

  /**
   * Primario.
   *
   * @param url URL JDBC.
   */
  public record Node(@NotBlank String url) {}

  /**
   * Réplica de lectura.
   *
   * @param name nombre publicado en logs y cabeceras (p. ej. {@code REPLICA_1}).
   * @param url URL JDBC.
   */
  public record Replica(@NotBlank String name, @NotBlank String url) {

    /**
     * Nodo de enrutamiento de la réplica.
     *
     * @return nodo.
     */
    public DbNode node() {
      return new DbNode(name);
    }
  }
}
