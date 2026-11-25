package com.distribuidos.inventory.exception;

/** Raised when a release would drive reserved stock below zero. */
public class InvalidStockReleaseException extends RuntimeException {

  private final String productCode;

  public InvalidStockReleaseException(String productCode) {
    super("No hay stock reservado suficiente para liberar " + productCode);
    this.productCode = productCode;
  }

  public String getProductCode() {
    return productCode;
  }
}
