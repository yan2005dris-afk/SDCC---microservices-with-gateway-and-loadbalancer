package com.distribuidos.order.datasource;

/** Nodo de base de datos fijado para la operación en curso del hilo actual. */
public final class RoutingContextHolder {

  private static final ThreadLocal<DbNode> CONTEXT = new ThreadLocal<>();

  private RoutingContextHolder() {}

  public static void set(DbNode node) {
    CONTEXT.set(node);
  }

  public static DbNode get() {
    return CONTEXT.get();
  }

  public static void clear() {
    CONTEXT.remove();
  }
}
