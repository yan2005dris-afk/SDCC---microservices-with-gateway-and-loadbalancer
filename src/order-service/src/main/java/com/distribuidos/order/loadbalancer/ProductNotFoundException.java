package com.distribuidos.order.loadbalancer;

/** El producto del pedido no existe en inventario ({@code 422}). */
public class ProductNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Crea la excepción.
   *
   * @param productCode producto.
   */
  public ProductNotFoundException(String productCode) {
    super("Producto no existe en inventario: " + productCode);
  }
}
