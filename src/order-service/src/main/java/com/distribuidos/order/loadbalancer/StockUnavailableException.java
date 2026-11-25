package com.distribuidos.order.loadbalancer;

/** Inventario no tiene stock suficiente para el pedido ({@code 400}). */
public class StockUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient Integer remainingStock;

  /**
   * Crea la excepción.
   *
   * @param productCode producto.
   * @param remainingStock stock disponible informado por inventario, si lo hay.
   */
  public StockUnavailableException(String productCode, Integer remainingStock) {
    super("Stock insuficiente para " + productCode);
    this.remainingStock = remainingStock;
  }

  /**
   * Stock disponible informado por inventario.
   *
   * @return stock, o {@code null} si no se informó.
   */
  public Integer remainingStock() {
    return remainingStock;
  }
}
