package com.distribuidos.order.dto;

/**
 * Solicitud fuertemente tipada para crear un nuevo pedido en {@code POST /api/orders}.
 *
 * <p>El precio no se acepta del cliente: lo fija inventario (ORD-06). Un {@code unitPrice} enviado
 * por clientes antiguos se ignora.
 *
 * @param customerName nombre del cliente (opcional; aplica valor por defecto si falta).
 * @param customerEmail correo del cliente (opcional; aplica valor por defecto si falta).
 * @param productCode código único del producto a reservar.
 * @param quantity cantidad solicitada del producto (mayor a cero).
 */
public record CreateOrderRequest(
    String customerName, String customerEmail, String productCode, Integer quantity) {

  private static final String DEFAULT_CUSTOMER_NAME = "Cliente General";
  private static final String DEFAULT_CUSTOMER_EMAIL = "cliente@distribuidos.com";

  /**
   * Verifica si los campos obligatorios para crear el pedido son válidos.
   *
   * @return {@code true} si código y cantidad son válidos; {@code false} en caso contrario.
   */
  public boolean hasRequiredFields() {
    return productCode != null && !productCode.isBlank() && quantity != null && quantity > 0;
  }

  /**
   * Resuelve el nombre del cliente usando un valor por defecto cuando no fue suministrado.
   *
   * @return nombre no vacío del cliente.
   */
  public String resolvedCustomerName() {
    return (customerName != null && !customerName.isBlank()) ? customerName : DEFAULT_CUSTOMER_NAME;
  }

  /**
   * Resuelve el correo del cliente usando un valor por defecto cuando no fue suministrado.
   *
   * @return correo electrónico no vacío del cliente.
   */
  public String resolvedCustomerEmail() {
    return (customerEmail != null && !customerEmail.isBlank())
        ? customerEmail
        : DEFAULT_CUSTOMER_EMAIL;
  }
}
