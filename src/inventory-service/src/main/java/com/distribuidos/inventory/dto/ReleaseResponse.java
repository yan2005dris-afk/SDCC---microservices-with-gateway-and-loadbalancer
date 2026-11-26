package com.distribuidos.inventory.dto;

import java.util.List;

/**
 * Resultado de liberar las reservas de un pedido.
 *
 * <p>Los campos de primer nivel ({@code productCode}, {@code quantity}, {@code message}) repiten el
 * primer producto para mantener compatible el JSON anterior; {@code releases} trae el detalle de
 * cada producto del pedido.
 *
 * @param orderNumber pedido liberado.
 * @param productCode primer producto liberado.
 * @param quantity cantidad del primer producto.
 * @param message mensaje del primer producto.
 * @param releases detalle por producto.
 */
public record ReleaseResponse(
    String orderNumber,
    String productCode,
    int quantity,
    String message,
    List<ReleasedItem> releases) {

  /** Mensaje de una liberación aplicada o ya aplicada antes (idempotente). */
  public static final String RELEASED = "Stock liberado exitosamente";

  /**
   * Crea la respuesta a partir del detalle por producto.
   *
   * @param orderNumber pedido.
   * @param releases detalle por producto (al menos uno).
   * @return respuesta.
   */
  public static ReleaseResponse of(String orderNumber, List<ReleasedItem> releases) {
    ReleasedItem first = releases.get(0);
    return new ReleaseResponse(
        orderNumber, first.productCode(), first.quantity(), first.message(), List.copyOf(releases));
  }

  /**
   * Liberación de un producto.
   *
   * @param productCode producto.
   * @param quantity cantidad devuelta al stock.
   * @param message mensaje.
   */
  public record ReleasedItem(String productCode, int quantity, String message) {

    /**
     * Liberación correcta de un producto.
     *
     * @param productCode producto.
     * @param quantity cantidad.
     * @return detalle.
     */
    public static ReleasedItem released(String productCode, int quantity) {
      return new ReleasedItem(productCode, quantity, RELEASED);
    }
  }
}
