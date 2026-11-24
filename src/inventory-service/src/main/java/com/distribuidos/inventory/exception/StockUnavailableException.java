package com.distribuidos.inventory.exception;

/** Raised when available stock is lower than the requested quantity. */
public class StockUnavailableException extends RuntimeException {

  private final String productCode;
  private final int availableStock;

  public StockUnavailableException(String productCode, int availableStock) {
    super("Stock insuficiente. Disponible: " + availableStock);
    this.productCode = productCode;
    this.availableStock = availableStock;
  }

  public String getProductCode() {
    return productCode;
  }

  public int getAvailableStock() {
    return availableStock;
  }
}
