package com.distribuidos.inventory.exception;

/** Raised when a reserve request targets a product that does not exist. */
public class ProductNotFoundException extends RuntimeException {

  private final String productCode;

  public ProductNotFoundException(String productCode) {
    super("Producto no encontrado: " + productCode);
    this.productCode = productCode;
  }

  public ProductNotFoundException(String productCode, String message) {
    super(message);
    this.productCode = productCode;
  }

  public String getProductCode() {
    return productCode;
  }
}
