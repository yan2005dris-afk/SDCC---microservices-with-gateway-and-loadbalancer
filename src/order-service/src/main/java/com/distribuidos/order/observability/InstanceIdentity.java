package com.distribuidos.order.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Identificador de esta instancia del microservicio (cabecera {@code X-Instance-Id} y logs). */
@Component
public class InstanceIdentity {

  private final String id;

  /**
   * Crea la identidad a partir de {@code INSTANCE_ID} o, si falta, del nombre y puerto.
   *
   * @param id identificador de la instancia.
   */
  public InstanceIdentity(
      @Value("${INSTANCE_ID:${spring.application.name}:${server.port:8081}}") String id) {
    this.id = id;
  }

  /**
   * Devuelve el identificador de la instancia.
   *
   * @return identificador, por ejemplo {@code order-service-1:8081}.
   */
  public String id() {
    return id;
  }
}
